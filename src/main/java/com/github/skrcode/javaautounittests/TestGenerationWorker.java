package com.github.skrcode.javaautounittests;

import com.github.skrcode.javaautounittests.DTOs.PromptInitialResponseOutput;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.google.genai.types.Schema;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TestGenerationWorker {

    private static final int MAX_ATTEMPTS= 10;

    public static void process(Project project, PsiClass cut, @NotNull ProgressIndicator indicator, PsiDirectory testRoot) {

        try {
            PsiDirectory packageDir = resolveTestPackageDir(project, testRoot, cut);
            if (packageDir == null) {
                indicator.setText("Cannot determine package for CUT");
                return;
            }

            String cutName = ReadAction.compute(() -> cut.isValid() ? cut.getName() : "<invalid>");
            String cutClass = CUTUtil.cleanedSourceForLLM(project, cut);
            String getSingleTestPromptPlaceholder = PromptBuilder.getPromptPlaceholder("get-single-test-prompt-0.0.8");
            String getSingleTestInitialPromptPlaceholder = PromptBuilder.getPromptPlaceholder("get-single-test-prompt-initial-0.0.8");
            String errorOutput = "";
            String testFileName = cutName + "Test.java";
            List<String> contextClasses = new ArrayList<>();
            // Attempts
            boolean isLLMGeneratedAtleastOnce = false;
            String existingIndividualTestClass = "";
            for (int attempt = 1; ; attempt++) {
                indicator.setText("Generating test : attempt" + attempt + "/" + MAX_ATTEMPTS);

                Ref<PsiFile> testFile = ReadAction.compute(() -> Ref.create(packageDir.findFile(testFileName)));
                if (ReadAction.compute(() -> testFile.get()) != null) {
                    indicator.setText("Compiling #" + attempt + "/" + MAX_ATTEMPTS + " : " + testFileName);
                    existingIndividualTestClass = testFile.get().getText();
                    errorOutput = BuilderUtil.compileJUnitClass(project, testFile);
                    if (errorOutput.isEmpty() && isLLMGeneratedAtleastOnce) break;
                    indicator.setText("Compiled #" + attempt + "/" + MAX_ATTEMPTS + ": " + testFileName);
                }

                if (attempt > MAX_ATTEMPTS) break;
                indicator.setText("Invoking LLM Attempt #" + attempt + "/" + MAX_ATTEMPTS);
                List<String> contextClassesSource = getSourceCodeOfContextClasses(project,contextClasses);

                if(existingIndividualTestClass.isEmpty()) {
                    Schema schema = JAIPilotLLM.getInitialSchema();
                    String fullText = JAIPilotLLM.getAllSingleTest(getSingleTestInitialPromptPlaceholder, testFileName, cutClass, existingIndividualTestClass, errorOutput, contextClassesSource, indicator, "gemini-2.5-flash-lite-preview-06-17", schema);
                    PromptInitialResponseOutput promptInitialResponseOutput = JAIPilotLLM.parseInitialPromptOutputText(fullText);
                    contextClasses = promptInitialResponseOutput.getContextClasses();
                    BuilderUtil.write(project, testFile, packageDir, testFileName, promptInitialResponseOutput.getTestClassCode());
                } else {
                    Schema schema = JAIPilotLLM.getSchema();
                    String fullText = JAIPilotLLM.getAllSingleTest(getSingleTestPromptPlaceholder, testFileName, cutClass, existingIndividualTestClass, errorOutput, contextClassesSource, indicator, "gemini-2.5-pro", schema);
                    PromptResponseOutput promptResponseOutput = JAIPilotLLM.parsePromptOutputText(fullText);
                    contextClasses = promptResponseOutput.getContextClasses();
                    BuilderUtil.writeDiff(project, testFile, packageDir, testFileName, promptResponseOutput.getTestClassCodeDiff());
                }
                isLLMGeneratedAtleastOnce = true;
                indicator.setText("Successfully invoked LLM Attempt #" + attempt + "/" + MAX_ATTEMPTS);

            }
            indicator.setText("Successfully generated Test Class " + testFileName);
        }
        catch (Throwable t) {
            t.printStackTrace();
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog("Exception: " + t.getClass().getName() + "\n" + t.getMessage(), "Error")
            );
        }
    }

    private static @Nullable PsiDirectory resolveTestPackageDir(Project project,
                                                                PsiDirectory testRoot,
                                                                PsiClass cut) {

        PsiPackage cutPkg = ReadAction.compute(() ->
                JavaDirectoryService.getInstance().getPackage(cut.getContainingFile().getContainingDirectory())
        );
        if (cutPkg == null) return null;

        String relPath = ReadAction.compute(() -> cutPkg.getQualifiedName().replace('.', '/'));
        return getOrCreateSubdirectoryPath(project, testRoot, relPath);
    }

    private static List<String> getSourceCodeOfContextClasses(Project project, List<String> contextClassesPath) {
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        List<String> result = new ArrayList<>();
        if(contextClassesPath == null) return result;
        for (String contextClassPath : contextClassesPath) {
            PsiClass psiClass = ReadAction.compute(() -> psiFacade.findClass(contextClassPath, scope));

            if (psiClass == null || !psiClass.isValid()) {
                result.add(contextClassPath+" is not valid. Attempt to change this.");
                continue;
            }

            PsiFile file = ReadAction.compute(() -> psiClass.getContainingFile());
            if (file == null || !file.isValid()) {
                result.add(contextClassPath+" is not valid. Attempt to change this.");
                continue;
            }
            String code = ReadAction.compute(file::getText);
            result.add(code);
        }

        return result;
    }


    /** Recursively find or create nested sub-directories like {@code org/example/service}. */
    private static @Nullable PsiDirectory getOrCreateSubdirectoryPath(Project project,
                                                                      PsiDirectory root,
                                                                      String relativePath) {
        return WriteCommandAction.writeCommandAction(project).compute(() -> {
            PsiDirectory current = root;
            for (String part : relativePath.split("/")) {
                PsiDirectory next = current.findSubdirectory(part);
                if (next == null) next = current.createSubdirectory(part);
                current = next;
            }
            return current;
        });
    }

    private TestGenerationWorker() {} // no-instantiation
}
