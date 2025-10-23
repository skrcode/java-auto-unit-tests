package com.github.skrcode.javaautounittests;

import com.github.javaparser.JavaParser;
import com.github.skrcode.javaautounittests.settings.ConsolePrinter;
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
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.skrcode.javaautounittests.CUTUtil.expandAll;

/**
 * Compiles a JUnit test class, runs it with coverage, and returns:
 *   • ""  → everything succeeded
 *   • compilation errors if compilation failed
 *   • structured summary of test execution (failures, ignored, etc.)
 *
 * Refocuses the JAIPilot Console after each run.
 */
public class BuilderUtil {

    private BuilderUtil() {}

    // --- Run JUnit class silently and parse TeamCity output ---
    public static String runJUnitClass(Project project, PsiFile psiFile) throws Exception {
        AtomicReference<ExecutionEnvironment> envRef = new AtomicReference<>();

        // Step 1: Build environment
        ApplicationManager.getApplication().invokeAndWait(() -> {
            JUnitConfigurationType configType = JUnitConfigurationType.getInstance();
            RunnerAndConfigurationSettings settings =
                    RunManager.getInstance(project).createConfiguration(
                            "Test Verifier", configType.getConfigurationFactories()[0]);

            JUnitConfiguration configuration = (JUnitConfiguration) settings.getConfiguration();

            @Nullable Module module = ModuleUtil.findModuleForPsiElement(psiFile);
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

        // Track results
        List<String> passedTests = new ArrayList<>();
        List<String> failedTests = new ArrayList<>();
        List<String> ignoredTests = new ArrayList<>();

        // Step 2: Run configuration silently
        ApplicationManager.getApplication().invokeAndWait(() -> {
            ProgramRunner<?> runner = ProgramRunner.getRunner(env.getExecutor().getId(), env.getRunProfile());
            if (runner == null) {
                failedTests.add("NO_RUNNER_FOUND");
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

                                if (line.startsWith("##teamcity[testStarted")) {
//                                    String name = extractAttr(line, "name");
//                                    passedTests.add(name);
                                } else if (line.startsWith("##teamcity[testFailed")) {
//                                    String name = extractAttr(line, "name");
//                                    String message = extractAttr(line, "message");
//                                    String details = extractAttr(line, "details");

                                    failedTests.add(line);

//                                    passedTests.remove(name);
                                } else if (line.startsWith("##teamcity[testIgnored")) {
                                    String name = extractAttr(line, "name");
//                                    ignoredTests.add(name);
//                                    passedTests.remove(name);
                                }
                            }

                            @Override
                            public void processTerminated(ProcessEvent event) {
                                latch.countDown();
                                // ✅ Refocus JAIPilot Console after tests finish
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    ToolWindow toolWindow =
                                            ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
                                    if (toolWindow != null) {
                                        toolWindow.show();
                                    }
                                });
                            }
                        });
                    } else {
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                failedTests.add("TEST_EXECUTION_ERROR: " + e.getMessage());
                latch.countDown();
            }
        });

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new Exception("Test Execution Timeout. Cannot run tests. Please fix IDE issues and retry.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Test Execution Interrupted. Cannot run tests. Please fix and retry.");
        }
        return joinLines(failedTests);
    }

    private static String extractAttr(String line, String key) {
        int idx = line.indexOf(key + "='");
        if (idx < 0) return "";
        int start = idx + key.length() + 2;
        int end = line.indexOf("'", start);
        return (end > start) ? line.substring(start, end) : "";
    }

    // --- Compile JUnit class silently ---
    public static String compileJUnitClass(Project project, Ref<PsiFile> testFile) {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder result = new StringBuilder();
        try {
            VirtualFile file = testFile.get().getVirtualFile();

            ApplicationManager.getApplication().invokeAndWait(() -> {
                CompilerManager.getInstance(project).compile(new VirtualFile[]{file}, (aborted, errors, warnings, context) -> {
                    if (aborted) {
                        result.append("COMPILATION_ABORTED");
                    } else if (errors > 0) {
                        result.append("COMPILATION_FAILED\n");
                        for (CompilerMessage msg : context.getMessages(CompilerMessageCategory.ERROR)) {
                            int line = ((CompilerMessageImpl) msg).getLine();
                            String codeLine = (line > 0)
                                    ? getLineFromVirtualFile(project, msg.getVirtualFile(), line)
                                    : "<unknown>";
                            result.append("Error at line " + line + ": " + codeLine + "\n" + msg.getMessage())
                                    .append("\n\n");
                        }
                    }
                    latch.countDown();
                    // ✅ Refocus JAIPilot Console after compile finishes
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ToolWindow toolWindow =
                                ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
                        if (toolWindow != null) {
                            toolWindow.show();
                        }
                    });
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
        }
        catch (Exception e) {
            result.append("Test Class not found");
        }

        return result.toString().trim();
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

    public static class TestMethod {
        public final String methodName;
        public final String fullImplementation;

        public TestMethod(String methodName, String fullImplementation) {
            this.methodName = methodName;
            this.fullImplementation = fullImplementation;
        }
    }


    @Nullable
    public static String buildAndWriteTestClass(Project project,
                                                Ref<PsiFile> testFile,
                                                PsiDirectory packageDir,
                                                String testFileName,
                                                String classSkeleton,
                                                List<TestMethod> methods,
                                                ConsoleView myConsole) {

        PsiFile existingFile = ReadAction.compute(testFile::get);
        boolean hasExisting = existingFile != null && existingFile.isValid();
        boolean hasSkeleton = classSkeleton != null && !classSkeleton.isBlank();
        boolean hasMethods  = methods != null && !methods.isEmpty();

        if (!hasExisting && !hasSkeleton) {
            System.err.println("[ERROR] Invalid: no existing test class and skeleton");
            return null; // <-- now return null on invalid
        }

        java.util.concurrent.atomic.AtomicReference<String> finalSourceRef = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
                PsiDocumentManager psiDocMgr = PsiDocumentManager.getInstance(project);
                PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

                // ✅ Step 0: Extract old test methods *as text* before any mutation
                List<String> oldTestMethodTexts = new ArrayList<>();
                if (hasExisting && existingFile instanceof PsiJavaFile jf && jf.getClasses().length > 0) {
                    PsiClass existingClass = jf.getClasses()[0];
                    for (PsiMethod old : existingClass.getMethods()) {
                        if (old.isConstructor()) continue;
                        boolean isTestAnnotated = Arrays.stream(old.getModifierList().getAnnotations())
                                .anyMatch(a -> a.getQualifiedName() != null && a.getQualifiedName().endsWith("Test"));
                        boolean nameLooksLikeTest = old.getName().startsWith("test");
                        if (isTestAnnotated || nameLooksLikeTest) {
                            oldTestMethodTexts.add(old.getText());
                        }
                    }
                }

                PsiFile psiFile;

                // ✅ Step 1: Create or overwrite skeleton
                if (hasSkeleton) {
                    if (hasExisting) {
                        Document doc = psiDocMgr.getDocument(existingFile);
                        if (doc != null) {
                            doc.setText(classSkeleton);
                            psiDocMgr.commitDocument(doc);
                            psiFile = existingFile;
                        } else {
                            psiFile = fileFactory.createFileFromText(testFileName, JavaFileType.INSTANCE, classSkeleton);
                            psiFile = (PsiFile) packageDir.add(psiFile);
                        }
                    } else {
                        psiFile = fileFactory.createFileFromText(testFileName, JavaFileType.INSTANCE, classSkeleton);
                        psiFile = (PsiFile) packageDir.add(psiFile);
                    }
                    testFile.set(psiFile);
                } else {
                    psiFile = existingFile;
                }

                if (!(psiFile instanceof PsiJavaFile javaFile)) {
                    finalSourceRef.set(null);
                    return;
                }
                if (javaFile.getClasses().length == 0) {
                    finalSourceRef.set(null);
                    return;
                }

                PsiClass psiClass = javaFile.getClasses()[0];

                // ✅ Step 2: Restore old @Test methods (avoids stale PSI)
                Set<String> seenNames = new HashSet<>();
                for (PsiMethod m : psiClass.getMethods()) seenNames.add(m.getName());
                for (String text : oldTestMethodTexts) {
                    try {
                        PsiMethod restored = elementFactory.createMethodFromText(text, psiClass);
                        if (!seenNames.contains(restored.getName())) {
                            psiClass.add(restored);
                            seenNames.add(restored.getName());
                        }
                    } catch (Exception e) {
                        System.err.println("[WARN] Failed to re-add old test: " + e.getMessage());
                    }
                }

                // ✅ Step 3: Merge response methods (add / replace / delete)
                if (hasMethods) {
                    Map<String, String> response = new LinkedHashMap<>();
                    Set<String> deletes = new HashSet<>();

                    for (TestMethod m : methods) {
                        if (m == null || m.methodName == null) continue;
                        String name = m.methodName.trim();
                        String impl = m.fullImplementation == null ? "" : m.fullImplementation.trim();
                        if (impl.isEmpty()) deletes.add(name);
                        else response.put(name, impl);
                    }

                    // Delete or replace
                    for (PsiMethod existing : psiClass.getMethods()) {
                        String name = existing.getName();
                        if (deletes.contains(name)) {
                            existing.delete();
                        } else if (response.containsKey(name)) {
                            try {
                                PsiMethod updated = elementFactory.createMethodFromText(response.get(name), psiClass);
                                existing.replace(updated);
                                response.remove(name);
                            } catch (Exception e) {
                                System.err.println("[WARN] Replace failed for " + name + ": " + e.getMessage());
                            }
                        }
                    }

                    // Add new ones
                    for (Map.Entry<String, String> e : response.entrySet()) {
                        try {
                            PsiMethod newMethod = elementFactory.createMethodFromText(e.getValue(), psiClass);
                            psiClass.add(newMethod);
                        } catch (Exception ex) {
                            System.err.println("[WARN] Skipping method '" + e.getKey() + "': " + ex.getMessage());
                        }
                    }
                }

                // ✅ Step 4: Commit & validate
                expandAll(project, (PsiJavaFile) psiFile);

                Document doc = psiDocMgr.getDocument(psiFile);
                if (doc != null) psiDocMgr.commitDocument(doc);
                String finalTestSource = ((PsiJavaFile) psiFile).getText();

                JavaParser parser = new JavaParser();
                if (!parser.parse(finalTestSource).getResult().isPresent()) {
                    throw new IllegalArgumentException("Error in final test file syntax.");
                }

                // ✅ Step 5: Log + set return value
                ConsolePrinter.success(myConsole, "✅ Test class composed and written successfully");
                ConsolePrinter.codeBlock(myConsole, Arrays.asList(finalTestSource));
                finalSourceRef.set(finalTestSource);

            } catch (Throwable t) {
                errorRef.set(t);
            }
        });

        if (errorRef.get() != null) {
            // Re-throw so callers can handle (or return null if you prefer)
            throw new RuntimeException("Failed to build/write test class", errorRef.get());
        }
        return finalSourceRef.get();
    }


    private static String joinLines(List<String> list) { if (list == null || list.isEmpty()) return ""; return String.join("\n", list); }
}
