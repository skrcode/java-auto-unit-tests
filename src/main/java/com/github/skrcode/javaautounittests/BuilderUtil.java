package com.github.skrcode.javaautounittests;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Compiles a JUnit test class, runs it with coverage, and returns:
 *   • ""  → everything succeeded
 *   • compilation errors if compilation failed
 *   • console output if test execution failed (non-zero exit or test failures)
 */
public class BuilderUtil {

    private BuilderUtil() {}

    public static String runJUnitClass(Project project, PsiFile psiFile) {
        AtomicReference<ExecutionEnvironment> envRef = new AtomicReference<>();

        // --- Step 1: build environment (EDT) ---
        ApplicationManager.getApplication().invokeAndWait(() -> {
            JUnitConfigurationType configType = JUnitConfigurationType.getInstance();
            RunnerAndConfigurationSettings settings =
                    RunManager.getInstance(project).createConfiguration(
                            "Test Verifier", configType.getConfigurationFactories()[0]);

            JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();

            Module module = ModuleUtil.findModuleForPsiElement(psiFile);
            if (module != null) configuration.setModule(module);

            if (psiFile instanceof PsiJavaFile psiJavaFile && psiJavaFile.getClasses().length > 0) {
                configuration.setMainClass(psiJavaFile.getClasses()[0]);
            }

            Executor executor = DefaultRunExecutor.getRunExecutorInstance();
            try {
                envRef.set(ExecutionEnvironmentBuilder.create(executor, settings).build());
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

        ExecutionEnvironment env = envRef.get();
        if (env == null) return "FAILED_TO_CREATE_ENV";

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder failures = new StringBuilder();

        // --- Step 2: run configuration (EDT) ---
        ApplicationManager.getApplication().invokeAndWait(() -> {
            ProgramRunner<?> runner = ProgramRunner.getRunner(env.getExecutor().getId(), env.getRunProfile());
            if (runner == null) {
                failures.append("NO_RUNNER_FOUND");
                latch.countDown();
                return;
            }

            try {
                runner.execute(env, descriptor -> {
                    ProcessHandler handler = descriptor.getProcessHandler();
                    if (handler != null) {
                        handler.addProcessListener(new ProcessAdapter() {
                            @Override
                            public void onTextAvailable(ProcessEvent event, Key outputType) {
                                String line = event.getText().trim();
                                if (line.startsWith("##teamcity[testFailed")) {
                                    String testName = extractAttr(line, "name");
                                    String message = extractAttr(line, "message");
                                    String details = extractAttr(line, "details");

                                    failures.append("Test: ").append(testName).append("\n")
                                            .append("Error: ").append(message).append("\n")
                                            .append(details.replace("|n", "\n"))
                                            .append("\n\n");
                                }
                            }
                            @Override
                            public void processTerminated(ProcessEvent event) {
                                latch.countDown();
                            }
                        });
                    } else {
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                failures.append("TEST_EXECUTION_ERROR: ").append(e.getMessage());
                latch.countDown();
            }
        });

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                return "TEST_EXECUTION_TIMEOUT";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "TEST_INTERRUPTED";
        }

        return failures.length() == 0 ? "" : failures.toString().trim();
    }

    private static String extractAttr(String line, String key) {
        int idx = line.indexOf(key + "='");
        if (idx < 0) return "";
        int start = idx + key.length() + 2;
        int end = line.indexOf("'", start);
        return (end > start) ? line.substring(start, end) : "";
    }





    public static String compileJUnitClass(Project project, Ref<PsiFile> testFile)  {

        String staticErrors = getAllErrorMessages(project, testFile.get());
        if(!staticErrors.isEmpty()) return staticErrors;

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();
        VirtualFile file = testFile.get().getVirtualFile();

        ApplicationManager.getApplication().invokeAndWait(() -> {
            CompilerManager.getInstance(project).compile(new VirtualFile[]{file}, (aborted, errors, warnings, context) -> {
                if (aborted) {
                    result.append("COMPILATION_ABORTED");
                } else if (errors > 0) {
                    result.append("COMPILATION_FAILED\n");
                    for (CompilerMessage msg : context.getMessages(CompilerMessageCategory.ERROR)) {
                        int line = ((CompilerMessageImpl) msg).getLine();
                        String codeLine = (line > 0) ? getLineFromVirtualFile(project, msg.getVirtualFile(), line) : "<unknown>";
                        result.append("Error at line :" + codeLine + "\n"+msg.getMessage()).append("\n\n");
                    }
                }
                latch.countDown();
            });
        });

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                result.append("COMPILATION_TIMEOUT");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.append("COMPILATION_INTERRUPTED");
        }

        return result.toString().trim();
    }

    public static String getAllErrorMessages(Project project, PsiFile psiFile) {
        Document doc = psiFile.getViewProvider().getDocument();

        // Collect all highlights
        List<HighlightInfo> infos = com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
                .getHighlights(doc, null, project);

        return infos.stream()
                .filter(info -> info.getSeverity().equals(com.intellij.lang.annotation.HighlightSeverity.ERROR))
                .map(info -> {
                    int errorLine = doc.getLineNumber(info.getStartOffset());

                    // Define range (clamped to file boundaries)
                    int startLine = Math.max(0, errorLine - 2);
                    int endLine = Math.min(doc.getLineCount() - 1, errorLine + 2);

                    StringBuilder context = new StringBuilder();
                    for (int line = startLine; line <= endLine; line++) {
                        int lineStartOffset = doc.getLineStartOffset(line);
                        int lineEndOffset = doc.getLineEndOffset(line);
                        String text = doc.getText(new TextRange(lineStartOffset, lineEndOffset));
                        context.append(line + 1).append(": ").append(text).append("\n");
                    }

                    return "Error: " + info.getDescription() + "\nContext:\n" + context;
                })
                .collect(Collectors.joining("\n---\n"));
    }


    private static String getLineFromVirtualFile(Project project, VirtualFile file, int lineNumber) {
        if (lineNumber < 1) return "<invalid line number>";

        Document doc = FileDocumentManager.getInstance().getDocument(file);
        if (doc == null) return "<document not found>";

        int lineIndex = lineNumber - 1;
        if (lineIndex >= doc.getLineCount()) return "<line number out of bounds>";

        int startOffset = doc.getLineStartOffset(lineIndex);
        int endOffset = doc.getLineEndOffset(lineIndex);

        return doc.getText(new TextRange(startOffset, endOffset)).trim();
    }


    public static void write(Project project,
                             Ref<PsiFile> testFile,
                             PsiDirectory packageDir,
                             String testFileName,
                             String testSource) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiFile existingFile = testFile.get();
            PsiFile fileToProcess;

            if (existingFile != null && existingFile.isValid()) {
                Document doc = PsiDocumentManager.getInstance(project).getDocument(existingFile);
                if (doc != null) {
                    doc.setText(testSource);
                    PsiDocumentManager.getInstance(project).commitDocument(doc);
                }
                fileToProcess = existingFile;
            } else {
                PsiFile newFile = PsiFileFactory.getInstance(project).createFileFromText(
                        testFileName, JavaFileType.INSTANCE, testSource);
                PsiFile addedFile = (PsiFile) packageDir.add(newFile);
                testFile.set(addedFile);
                fileToProcess = addedFile;
            }

            // ✅ Optimize imports
            JavaCodeStyleManager.getInstance(project).optimizeImports(fileToProcess);

            // ✅ Rearrange entries
//            CodeStyleManager.getInstance(project).(fileToProcess);
            new ReformatCodeProcessor(project, fileToProcess, null, false).run();

            // ✅ Cleanup code (reformat)
            CodeStyleManager.getInstance(project).reformat(fileToProcess);
        });
    }


}
