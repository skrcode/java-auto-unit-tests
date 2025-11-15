// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.Message;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.settings.ConsolePrinter;
import com.github.skrcode.javaautounittests.settings.JAIPilotExecutionManager;
import com.github.skrcode.javaautounittests.settings.telemetry.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.nio.file.Paths;
import java.util.*;

public final class TestGenerationWorker {

    private static final int MAX_ATTEMPTS = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void process(Project project, PsiClass cut, @NotNull ConsoleView myConsole, PsiDirectory testRoot, @NotNull ProgressIndicator indicator) {
        int attempt = 1;
        boolean isCacheUsedTestPlan = false, isCacheUsedApply = false;
        try {

            long start = System.nanoTime();

            PsiDirectory packageDir = resolveTestPackageDir(project, testRoot, cut);
            if (packageDir == null) {
                Telemetry.genFailed(null,String.valueOf(attempt),"Cannot determine package for CUT");
                ConsolePrinter.error(myConsole, "Cannot determine package for CUT");
                return;
            }

            String cutName = ReadAction.compute(() -> cut.isValid() ? cut.getName() : "<invalid>");
            String testFileName = cutName + "Test.java";
            Telemetry.allGenBegin(testFileName);

            boolean isLLMGeneratedAtLeastOnce = false;

            JAIPilotExecutionManager.reset();
            List<Message> messages = new ArrayList<>();
            String cutSource = CUTUtil.cleanedSourceForLLM(project, cut);
            messages.add(JAIPilotLLM.getMessage(JAIPilotLLM.USER_ROLE,cutSource));

            // Add existing test class (if present) as context
            Ref<PsiFile> testFileExisting = ReadAction.compute(() -> Ref.create(packageDir.findFile(testFileName)));
            String existingTestSource = "";
            if (ReadAction.compute(testFileExisting::get) != null) {
                existingTestSource = ReadAction.compute(() -> testFileExisting.get().getText());
                if (existingTestSource != null && !existingTestSource.isBlank()) {
                    messages.add(JAIPilotLLM.getMessage(JAIPilotLLM.USER_ROLE,existingTestSource));
                }
            }
            boolean shouldRebuild = true;
            Set<String> isClassPathFetched = new HashSet<>();
            for (; ; attempt++) {
                ConsolePrinter.section(myConsole, "Attempting");
                // Check if test file already exists and run it
                Ref<PsiFile> testFile = ReadAction.compute(() -> Ref.create(packageDir.findFile(testFileName)));
                if (ReadAction.compute(testFile::get) != null && shouldRebuild) {
                    ConsolePrinter.info(myConsole, "Compiling Tests " + testFileName);
                    indicator.checkCanceled();
                    String errorOutput = BuilderUtil.compileJUnitClass(project, testFile);

                    if (errorOutput.isEmpty()) {
                        ConsolePrinter.success(myConsole, "Compilation Successful " + testFileName);
                        ConsolePrinter.info(myConsole, "Running Tests " + testFileName);
                        indicator.checkCanceled();
                        errorOutput = BuilderUtil.runJUnitClass(project, testFile.get());
                        if(!errorOutput.isEmpty()) {
                            ConsolePrinter.info(myConsole, "Found tests execution errors " + testFileName);
                            messages.add(JAIPilotLLM.getMessage(JAIPilotLLM.USER_ROLE,errorOutput));
                        }
                        else {
                            ConsolePrinter.success(myConsole, "Tests execution successful " + testFileName);
                            if(isLLMGeneratedAtLeastOnce) break;
                        }
                    }
                    else {
                        ConsolePrinter.info(myConsole, "Found compilation errors " + testFileName);
                        messages.add(JAIPilotLLM.getMessage(JAIPilotLLM.USER_ROLE,errorOutput));
                    }
                }
                shouldRebuild = false;
                if (attempt > MAX_ATTEMPTS) {
                    ConsolePrinter.warn(myConsole, "Attempts breached. I have tried my best to compile and execute tests. Please fix the remaining tests manually. " + testFileName);
                    break;
                }

                ConsolePrinter.info(myConsole, "Generating tests " + testFileName +" Please wait....");
                indicator.checkCanceled();
                PromptResponseOutput output = JAIPilotLLM.generateContent(
                        testFileName,
                        messages,
                        myConsole,
                        attempt,
                        indicator
                );
                messages.add(output.getMessage());
                if (output.getMessage().getContentAsList() != null) {
                    List<Message.MessageContent> messageContents = new ArrayList<>();
                    for (int i=0;i<10 && i < output.getMessage().getContentAsList().size();i++) {
                        Message.MessageContent messageContent = MAPPER.convertValue(output.getMessage().getContentAsList().get(i),Message.MessageContent.class);
                        if (messageContent.getType().equals("tool_use")) {
                            String fn = messageContent.getName();
                            Object args = messageContent.getInput();
                            String toolUseId = messageContent.getId();
                            switch (fn) {
                                case "plan_test_changes": {
                                    if (args instanceof Map) {
                                        Map<?, ?> argMap = (Map<?, ?>) args;
                                        String testPlan = (String) argMap.get("testPlan");
                                        ConsolePrinter.info(myConsole, "Fetching test plan: \n" + testPlan);
                                        messageContents.add(JAIPilotLLM.getMessageToolResultContent(toolUseId, testPlan, !isCacheUsedTestPlan));
                                        isCacheUsedTestPlan = true;
                                    }
                                    break;
                                }
                                case "apply_test_class": {
                                    if (args instanceof Map) {
                                        Map<?, ?> argMap = (Map<?, ?>) args;
                                        isLLMGeneratedAtLeastOnce = true;
                                        try {
                                            indicator.checkCanceled();
                                            String classSkeleton = (String) argMap.get("classSkeleton");
                                            List<BuilderUtil.TestMethod> methods = new ArrayList<>();
                                            Object rawMethods = argMap.get("methods");
                                            if (rawMethods instanceof Map<?, ?> methodsMap) {
                                                for (Map.Entry<?, ?> entry : methodsMap.entrySet()) {
                                                    String methodName = Objects.toString(entry.getKey(), null);
                                                    String fullImpl = Objects.toString(entry.getValue(), "");
                                                    methods.add(new BuilderUtil.TestMethod(methodName, fullImpl));
                                                }
                                            }
                                            // Build and write the test class
                                            String newTestSource = BuilderUtil.buildAndWriteTestClass(project, testFile, packageDir, testFileName, classSkeleton, methods, myConsole);
                                            messageContents.add(JAIPilotLLM.getMessageToolResultContent(toolUseId, newTestSource, !isCacheUsedApply));
                                            isCacheUsedApply=true;
                                            shouldRebuild = true;
                                        } catch (Exception e) {
                                            ConsolePrinter.info(myConsole, "⚠️ Error composing test class: " + e.getMessage());
                                            continue;
                                        }
                                    }
                                    break;
                                }

                                case "fetch_mockito_version":
                                    ConsolePrinter.info(myConsole, "Fetching mockito version");
                                    messageContents.add(JAIPilotLLM.getMessageToolResultContent(toolUseId,  CUTUtil.findMockitoVersion(project),false) );
                                    break;
                                case "get_file":
                                    if (args instanceof Map) {
                                        Map<?, ?> argMap = (Map<?, ?>) args;
                                        String filePath = (String) argMap.get("filePath");

                                        ConsolePrinter.info(myConsole, "Fetching file details: " + filePath);

                                        if(isClassPathFetched.contains(filePath) || filePath.endsWith(testFileName) || filePath.endsWith(cutName+".java")) {
                                            ConsolePrinter.info(myConsole, "Duplicate file - ignoring");
                                            messageContents.add(JAIPilotLLM.getMessageToolResultContent(toolUseId,  "Duplicate file - ignoring", false));
                                            continue;
                                        }
                                        isClassPathFetched.add(filePath);
                                        String toolResult = stripCommentsAndMethodBodies(project, filePath);

                                        if (StringUtils.isEmpty(toolResult)) {
                                            ConsolePrinter.info(myConsole, "No matches or file not found: " + filePath);
                                            toolResult = filePath + " not found or no matches found.";
                                        } else {
                                            ConsolePrinter.success(myConsole, "Snippet(s): " + toolResult);
                                            ConsolePrinter.success(myConsole, "Fetched file snippet(s): " + filePath);
                                        }
                                        messageContents.add(JAIPilotLLM.getMessageToolResultContent(toolUseId,  toolResult, false));
                                    }
                                    break;
                                case "terminate_call":
                                    ConsolePrinter.warn(myConsole, "Attempts breached. I have tried my best to compile and execute tests. Please fix the remaining tests manually. ");
                                    Telemetry.allGenError(String.valueOf(attempt), "terminate call");
                                    return;
                            }
                        }
                    }
                    messages.add(JAIPilotLLM.getMessageToolResult(JAIPilotLLM.USER_ROLE,messageContents));
                }

                // Server stop condition
                if (output.getErrorCode() / 100 == 4) {
                    throw new Exception(output.getErrorBody());
                }

                if (output.getErrorCode() == 504) {
                    ConsolePrinter.warn(myConsole,
                            "This class is too large for JAIPilot Free. Please upgrade to JAIPilot Pro to generate JUnit tests for larger classes.");
                    break;
                }

            }

            long end = System.nanoTime();
            Telemetry.allGenDone(testFileName, String.valueOf(attempt), (end - start) / 1_000_000);

            ConsolePrinter.section(myConsole, "Summary");
            ConsolePrinter.success(myConsole, "Successfully generated Test Class " + testFileName);
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("JAIPilot - One-Click AI Agent for Java Unit Testing Feedback")
                    .createNotification(
                            "All tests generated!",
                            "If JAIPilot helped you, please <a href=\"https://plugins.jetbrains.com/plugin/27706-jaipilot--ai-unit-test-generator/edit/reviews/new\">leave a review</a> and ⭐️ rate it - it helps a lot!",
                            NotificationType.INFORMATION
                    )
                    .setListener((notification, event) -> {
                        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            BrowserUtil.browse(event.getURL().toString());
                            notification.expire();
                        }
                    })
                    .notify(project);

        } catch (Throwable t) {
            Telemetry.allGenError(String.valueOf(attempt), t.getMessage());
            ConsolePrinter.error(myConsole, "Generation failed: " + t.getMessage());

            t.printStackTrace();
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog(t.getMessage(), "Error. Please retry in a few minutes.")
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

    private static VirtualFile findInProjectAndLibraries(Project project, String relativePath) {
        // 1. Search in content + source roots
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
            VirtualFile vf = VfsUtilCore.findRelativeFile(relativePath, root);
            if (vf != null) return vf;
        }

        // 2. Search in libraries (class roots, including JARs)
        for (VirtualFile root : ProjectRootManager.getInstance(project).orderEntries().classes().getRoots()) {
            VirtualFile vf = VfsUtilCore.findRelativeFile(relativePath, root);
            if (vf != null) return vf;
        }

        return null;
    }

    public static String getSourceCodeOfContextClasses(Project project, String relativePathOrFqcn) {
        if (relativePathOrFqcn == null || relativePathOrFqcn.isBlank()) {
            return "";
        }

        String normPath = relativePathOrFqcn.replace("\\", "/");

        // --- Try existing project/library search ---
        VirtualFile vf = findInProjectAndLibraries(project, normPath);
        if (vf != null) {
            PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(vf));
            if (psiFile != null && psiFile.isValid()) {
                return ReadAction.compute(psiFile::getText);
            }
        }

        // --- Fallback: try resolving as fully qualified class name ---
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);

        PsiClass psiClass = ReadAction.compute(() -> psiFacade.findClass(relativePathOrFqcn.replace("/", ".").replace(".java", ""), scope));
        if (psiClass != null && psiClass.isValid()) {
            PsiFile psiFile = ReadAction.compute(psiClass::getContainingFile);
            if (psiFile != null && psiFile.isValid()) {
                return ReadAction.compute(psiFile::getText);
            }
        }

        return "";
    }


    public static String stripCommentsAndMethodBodies(Project project, String relativePathOrFqcn) {
        if (relativePathOrFqcn == null || relativePathOrFqcn.isBlank()) return "";

        // --- Reuse existing utility to get raw source code text ---
        String sourceText = getSourceCodeOfContextClasses(project, relativePathOrFqcn);
        if (sourceText.isBlank()) return "";

        // --- Create temporary PSI file from text ---
        PsiJavaFile psiFile = (PsiJavaFile) PsiFileFactory.getInstance(project)
                .createFileFromText(
                        Paths.get(relativePathOrFqcn).getFileName().toString(), // fallback name
                        JavaFileType.INSTANCE,
                        sourceText
                );

        // --- Remove all comments (Javadoc, line, block) ---
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(psiFile, PsiComment.class)) {
            comment.delete();
        }

        // --- Replace all method/constructor bodies with "{}" ---
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod.class)) {
            PsiCodeBlock body = method.getBody();
            if (body != null) {
                body.replace(factory.createCodeBlock());
            }
        }

        // --- Replace all static/instance initializer bodies with "{}" ---
        for (PsiClassInitializer initializer : PsiTreeUtil.findChildrenOfType(psiFile, PsiClassInitializer.class)) {
            PsiCodeBlock body = initializer.getBody();
            if (body != null) {
                body.replace(factory.createCodeBlock());
            }
        }

        return psiFile.getText();
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
