package com.github.skrcode.javaautounittests;

import com.github.skrcode.javaautounittests.DTOs.Prompt;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.settings.AISettings;
import com.github.skrcode.javaautounittests.settings.JAIPilotConsoleManager;
import com.github.skrcode.javaautounittests.settings.telemetry.Telemetry;
import com.google.genai.types.Content;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
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

    private static final int MAX_ATTEMPTS= 20;

    public static void process(Project project, PsiClass cut, @NotNull ConsoleView myConsole, PsiDirectory testRoot) {

        int attempt = 1;
        try {
            long start = System.nanoTime();
            PsiDirectory packageDir = resolveTestPackageDir(project, testRoot, cut);
            if (packageDir == null) {
                JAIPilotConsoleManager.print(myConsole,"Cannot determine package for CUT", ConsoleViewContentType.ERROR_OUTPUT);
                return;
            }

            String cutName = ReadAction.compute(() -> cut.isValid() ? cut.getName() : "<invalid>");
            String cutClass = CUTUtil.cleanedSourceForLLM(project, cut);
            Prompt prompt = new Prompt();
            String errorOutput = "";
            String testFileName = cutName + "Test.java";
            Telemetry.allGenBegin(testFileName);

            List<Content> contentsContext = new ArrayList<>();
            List<Content> allSourceCodeOfContextClasses = new ArrayList<>();

            contentsContext.add(JAIPilotLLM.getInputClassContent(prompt, cutClass));
            // Attempts
            boolean isLLMGeneratedAtleastOnce = false;
            String existingIndividualTestClass = "";
            for (; ; attempt++) {
                List<Content> contentsJUnit = new ArrayList<>();
                contentsJUnit.add(JAIPilotLLM.getInputClassContent(prompt, cutClass));
                JAIPilotConsoleManager.print(myConsole,"Generating test : attempt" + attempt + "/" + MAX_ATTEMPTS,ConsoleViewContentType.NORMAL_OUTPUT);

                Ref<PsiFile> testFile = ReadAction.compute(() -> Ref.create(packageDir.findFile(testFileName)));
                if (ReadAction.compute(() -> testFile.get()) != null) {
                    JAIPilotConsoleManager.print(myConsole,"Compiling #" + attempt + "/" + MAX_ATTEMPTS + " : " + testFileName,ConsoleViewContentType.NORMAL_OUTPUT);
                    existingIndividualTestClass = ReadAction.compute(() -> testFile.get().getText());
                    contentsJUnit.add(JAIPilotLLM.getExistingTestClassContent(prompt,existingIndividualTestClass, "model"));
                    contentsContext.add(JAIPilotLLM.getExistingTestClassContent(prompt,existingIndividualTestClass, "user"));
                    errorOutput = BuilderUtil.compileJUnitClass(project, testFile);
                    if (errorOutput.isEmpty()) {
                        errorOutput = BuilderUtil.runJUnitClass(project, testFile.get());
                        if (errorOutput.isEmpty() && isLLMGeneratedAtleastOnce) break;
                    }
                    JAIPilotConsoleManager.print(myConsole,"Compiled #" + attempt + "/" + MAX_ATTEMPTS + ": " + testFileName,ConsoleViewContentType.NORMAL_OUTPUT);
                }

                if (attempt > MAX_ATTEMPTS) break;
                if (!errorOutput.isEmpty()) {
                    contentsJUnit.add(JAIPilotLLM.getErrorOutputContent(prompt, errorOutput));
                    contentsContext.add(JAIPilotLLM.getErrorOutputContent(prompt, errorOutput));
                }
                else if(!existingIndividualTestClass.isEmpty()) contentsJUnit.add(JAIPilotLLM.getGenerateMoreTestsContent(prompt, testFileName));

                for(int contextClassAttempt = 1;contextClassAttempt<=MAX_ATTEMPTS/3;contextClassAttempt++) {
                    contentsContext.add(JAIPilotLLM.getSystemInstructionContextContent(prompt, CUTUtil.findMockitoVersion(project)));
                    PromptResponseOutput allSingleTestContext = JAIPilotLLM.getAllSingleTestContext(contentsContext, prompt, testFileName,  contextClassAttempt, myConsole, CUTUtil.findMockitoVersion(project));
                    if(allSingleTestContext.getContextClasses().size() == 0) break;
                    contentsContext.add(JAIPilotLLM.getClassContextPathContent(allSingleTestContext.getContextClasses()));
                    Content sourceCodeOfContextClasses = JAIPilotLLM.getClassContextPathSourceContent(prompt, getSourceCodeOfContextClasses(project, allSingleTestContext.getContextClasses()));
                    contentsContext.add(sourceCodeOfContextClasses);
                    allSourceCodeOfContextClasses.add(sourceCodeOfContextClasses);
                }

                JAIPilotConsoleManager.print(myConsole,"Invoking LLM Attempt #" + attempt + "/" + MAX_ATTEMPTS,ConsoleViewContentType.NORMAL_OUTPUT);
                PromptResponseOutput promptResponseOutput;
                if(AISettings.getInstance().getMode().equals("Pro"))
                    promptResponseOutput  = JAIPilotLLM.getAllSingleTest( contentsJUnit, prompt, testFileName, attempt, myConsole, CUTUtil.findMockitoVersion(project));
                else promptResponseOutput = JAIPilotLLM.getAllSingleTest( contentsJUnit, prompt, testFileName, attempt, myConsole ,CUTUtil.findMockitoVersion(project));
                if(!Objects.isNull(promptResponseOutput.getTestClassCode())) {
                    isLLMGeneratedAtleastOnce = true;
                    JAIPilotConsoleManager.print(myConsole,"Successfully invoked LLM Attempt #" + attempt + "/" + MAX_ATTEMPTS,ConsoleViewContentType.NORMAL_OUTPUT);
                    BuilderUtil.write(project, testFile, packageDir, testFileName, promptResponseOutput.getTestClassCode());
                }
            }
            long end = System.nanoTime();
            Telemetry.allGenDone(testFileName, String.valueOf(attempt), (end - start) / 1_000_000);
            JAIPilotConsoleManager.print(myConsole,"Successfully generated Test Class " + testFileName,ConsoleViewContentType.NORMAL_OUTPUT);
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
                                                                PsiClass cut) throws Exception {

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
                                                                      String relativePath) throws Exception {
        try {
            return WriteCommandAction.writeCommandAction(project).compute(() -> {
                PsiDirectory current = root;
                for (String part : relativePath.split("/")) {
                    PsiDirectory next = current.findSubdirectory(part);
                    if (next == null) {
                        next = current.createSubdirectory(part);
                    }
                    current = next;
                }
                return current;
            });
        } catch (Exception e) {
            throw new Exception("Incorrect tests source directory");
        }
    }


    private TestGenerationWorker() {} // no-instantiation
}
