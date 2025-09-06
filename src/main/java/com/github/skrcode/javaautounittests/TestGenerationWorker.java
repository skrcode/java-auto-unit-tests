package com.github.skrcode.javaautounittests;

import com.github.skrcode.javaautounittests.DTOs.Prompt;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.github.skrcode.javaautounittests.settings.Telemetry;
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
import java.util.Objects;

public final class TestGenerationWorker {

    private static final int MAX_ATTEMPTS= 10;

    public static void process(Project project, PsiClass cut, @NotNull ProgressIndicator indicator, PsiDirectory testRoot) {

        int attempt = 1;
        String errorOutput = "";
        try {
            long start = System.nanoTime();
            PsiDirectory packageDir = resolveTestPackageDir(project, testRoot, cut);
            if (packageDir == null) {
                indicator.setText("Cannot determine package for CUT");
                return;
            }

            String cutName = ReadAction.compute(() -> cut.isValid() ? cut.getName() : "<invalid>");
            String cutClass = CUTUtil.cleanedSourceForLLM(project, cut);
            Prompt prompt = new Prompt();
            prompt.setGenerateMorePlaceholder(PromptBuilder.getPromptPlaceholder("generate-more-prompt"));
            prompt.setSystemInstructionsPlaceholder(PromptBuilder.getPromptPlaceholder("systeminstructions-prompt"));
            prompt.setErrorOutputPlaceholder(PromptBuilder.getPromptPlaceholder("erroroutput-prompt"));
            prompt.setInputPlaceholder(PromptBuilder.getPromptPlaceholder("input-prompt"));
            prompt.setExistingTestClassPlaceholder(PromptBuilder.getPromptPlaceholder("testclass-prompt"));
            errorOutput = "";
            String testFileName = cutName + "Test.java";
            Telemetry.allGenBegin(testFileName);
            List<String> contextClasses = new ArrayList<>();
            // Attempts
            boolean isLLMGeneratedAtleastOnce = false;
            String existingIndividualTestClass = "";
            for (; ; attempt++) {
                indicator.setText("Generating test : attempt" + attempt + "/" + MAX_ATTEMPTS);

                Ref<PsiFile> testFile = ReadAction.compute(() -> Ref.create(packageDir.findFile(testFileName)));
                if (ReadAction.compute(() -> testFile.get()) != null) {
                    indicator.setText("Compiling #" + attempt + "/" + MAX_ATTEMPTS + " : " + testFileName);
                    existingIndividualTestClass = ReadAction.compute(() -> testFile.get().getText());
                    errorOutput = BuilderUtil.compileJUnitClass(project, testFile);
                    if (errorOutput.isEmpty() && isLLMGeneratedAtleastOnce) break;
//                        errorOutput = BuilderUtil.runJUnitClass(project, testFile.get());
//                        if (errorOutput.isEmpty() ) break;
//                    }
                    indicator.setText("Compiled #" + attempt + "/" + MAX_ATTEMPTS + ": " + testFileName);
                }

                if (attempt > MAX_ATTEMPTS) break;
                indicator.setText("Invoking LLM Attempt #" + attempt + "/" + MAX_ATTEMPTS);
                List<String> contextClassesSource = getSourceCodeOfContextClasses(project,contextClasses);
                PromptResponseOutput promptResponseOutput;
                if(AISettings.getInstance().getMode().equals("Pro"))
                    promptResponseOutput  = JAIPilotLLM.getAllSingleTestPro(testFileName, cutClass, existingIndividualTestClass, errorOutput, contextClassesSource, attempt, indicator);
                else promptResponseOutput = JAIPilotLLM.getAllSingleTest( prompt, testFileName, cutClass, existingIndividualTestClass, errorOutput, contextClassesSource, attempt, indicator);
                if(!Objects.isNull(promptResponseOutput.getTestClassCode())) {
                    contextClasses = promptResponseOutput.getContextClasses();
                    isLLMGeneratedAtleastOnce = true;
                    indicator.setText("Successfully invoked LLM Attempt #" + attempt + "/" + MAX_ATTEMPTS);
                    BuilderUtil.write(project, testFile, packageDir, testFileName, promptResponseOutput.getTestClassCode());
                }
            }
            long end = System.nanoTime();
            Telemetry.allGenDone(testFileName, String.valueOf(attempt), (end - start) / 1_000_000);
            indicator.setText("Successfully generated Test Class " + testFileName);
        }
        catch (Throwable t) {
            Telemetry.allGenError(String.valueOf(attempt), t.getMessage());
            t.printStackTrace();
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog(t.getMessage(), "Error")
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
