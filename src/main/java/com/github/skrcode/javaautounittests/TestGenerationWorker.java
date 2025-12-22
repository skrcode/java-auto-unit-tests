// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.CacheDTO;
import com.github.skrcode.javaautounittests.DTOs.Message;
import com.github.skrcode.javaautounittests.DTOs.PromptResponseOutput;
import com.github.skrcode.javaautounittests.DTOs.QuotaResponse;
import com.github.skrcode.javaautounittests.llm.JAIPilotLLM;
import com.github.skrcode.javaautounittests.settings.*;
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
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.*;

import static com.github.skrcode.javaautounittests.llm.JAIPilotLLM.*;
import static com.github.skrcode.javaautounittests.settings.telemetry.TelemetryService.getAppVersion;

public final class TestGenerationWorker {

    private static final int MAX_ATTEMPTS = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void process(Project project, PsiClass cut, @NotNull ConsoleView myConsole, PsiDirectory testRoot, @NotNull ProgressIndicator indicator) {
        int attempt = 1;
        boolean isCacheUsedTestPlan = false;
        LLMGetFilesCache llmGetFilesCache = LLMGetFilesCache.getInstance(project);
        try {
            try {
                QuotaResponse quotaResponse = QuotaUtil.fetchQuota();
                if(quotaResponse.message != null)
                    ConsolePrinter.warn(myConsole, quotaResponse.message);
            }
            catch (Exception e) {}
            long start = System.nanoTime();

            PsiDirectory packageDir = resolveTestPackageDir(project, testRoot, cut);
            if (packageDir == null) {
                Telemetry.genFailed(null,String.valueOf(attempt),"Cannot determine package for CUT");
                ConsolePrinter.error(myConsole, "Cannot determine package for CUT");
                return;
            }

            String cutName = ReadAction.compute(() -> cut.isValid() ? cut.getName() : "<invalid>");
            String testFileName = cutName + "Test.java";

            String cutFqn = ReadAction.compute(() -> cut.isValid() ? cut.getQualifiedName() : null);
            List<String> cachedPaths = Collections.emptyList();

            if (cutFqn != null) {
                CacheDTO entry = llmGetFilesCache.get(cutFqn);
                if (entry != null && entry.files != null) {
                    cachedPaths = entry.files;
                }
            }

            Telemetry.allGenBegin(testFileName);

            boolean isLLMGeneratedAtLeastOnce = false;

            JAIPilotExecutionManager.reset();
            List<Message> messages = new ArrayList<>();
            String cutSource = CUTUtil.cleanedSourceForLLM(project, cut);
            messages.add(JAIPilotLLM.getMessage(JAIPilotLLM.USER_ROLE,testFileName));
            messages.add(JAIPilotLLM.getMessage(JAIPilotLLM.USER_ROLE,cutSource)); // get cut tool
            messages.add(JAIPilotLLM.getMessage(JAIPilotLLM.USER_ROLE,"Mockito version = "+CUTUtil.findMockitoVersion(project)));
            // Add existing test class (if present) as context
            Ref<PsiFile> testFileExisting = ReadAction.compute(() -> Ref.create(packageDir.findFile(testFileName)));
            String existingTestSource = "";
            if (ReadAction.compute(testFileExisting::get) != null) {
                existingTestSource = ReadAction.compute(() -> testFileExisting.get().getText());
            }
            messages.add(JAIPilotLLM.getMessage(USER_ROLE,testFileName+" = \n"+existingTestSource));

            // cache get
            Set<String> isClassPathFetched = new HashSet<>();
            for(String cachedFilePath: cachedPaths) {
                ConsolePrinter.info(myConsole, "Fetching file details from cache: " + cachedFilePath);
                if(isClassPathFetched.contains(cachedFilePath) || cachedFilePath.endsWith(testFileName) || cachedFilePath.endsWith(cutName+".java")) {
                    ConsolePrinter.info(myConsole, "Duplicate file - ignoring");
                    continue;
                }
                isClassPathFetched.add(cachedFilePath);
                String cachedFileContent = stripCommentsAndMethodBodies(project, cachedFilePath);
                if (StringUtils.isEmpty(cachedFileContent)) {
                    ConsolePrinter.info(myConsole, "No matches or file not found: " + cachedFileContent);
                } else {
                    messages.add(JAIPilotLLM.getMessage(USER_ROLE,cachedFilePath +"=\n"+cachedFileContent));
                    ConsolePrinter.success(myConsole, "Cached Snippet(s): " + cachedFileContent);
                    ConsolePrinter.success(myConsole, "Fetched cached file snippet(s): " + cachedFileContent);
                }
            }

            List<Message> actualMessages = new ArrayList<>(messages);
            boolean shouldRebuild = true;
            String newTestSource = null;
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
                            actualMessages.add(JAIPilotLLM.getMessage(USER_ROLE,errorOutput));
                        }
                        else {
                            ConsolePrinter.success(myConsole, "Tests execution successful " + testFileName);
                            if(isLLMGeneratedAtLeastOnce) break;
                        }
                    }
                    else {
                        ConsolePrinter.info(myConsole, "Found compilation errors " + testFileName);
                        actualMessages.add(JAIPilotLLM.getMessage(USER_ROLE,errorOutput));
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
                        actualMessages,
                        myConsole,
                        attempt,
                        indicator
                );
                actualMessages = new ArrayList<>(messages);
                if (output.getMessage() != null) {
                    List<Message.MessageContent> messageContentsUser = new ArrayList<>(), actualMessageContentsUser = new ArrayList<>(), messageContentsModel = new ArrayList<>(), actualMessageContentsModel = new ArrayList<>();
                    for (int i=0;i < output.getMessage().getContentAsList().size();i++) {
                        Message.MessageContent messageContent = MAPPER.convertValue(output.getMessage().getContentAsList().get(i),Message.MessageContent.class);
                        if (messageContent.getType().equals("thinking") || messageContent.getType().equals("redacted_thinking")) {
                            actualMessageContentsModel.add(messageContent);
                            messageContentsModel.add(messageContent);
                        }
                        if (messageContent.getType().equals("tool_use")) {
                            String fn = messageContent.getName();
                            Message.MessageContent.Input args = messageContent.getInput();
                            String toolUseId = messageContent.getId();
                            switch (fn) {
                                case "plan_test_changes": {
                                    String testPlan = args.getTestPlan();
                                    ConsolePrinter.info(myConsole, "Fetching test plan: \n" + testPlan);
                                    actualMessageContentsModel.add(getMessageToolRequestContent(toolUseId, fn, args));
                                    messageContentsModel.add(getMessageToolRequestContent(toolUseId, fn, args));
                                    actualMessageContentsUser.add(getMessageToolResultContent(toolUseId, testPlan, !isCacheUsedTestPlan));
                                    messageContentsUser.add(getMessageToolResultContent(toolUseId, testPlan, !isCacheUsedTestPlan));
                                    isCacheUsedTestPlan = true;
                                    break;
                                }
                                case "apply_test_class": {
                                    isLLMGeneratedAtLeastOnce = true;
                                    try {
                                        indicator.checkCanceled();
                                        String classSkeleton = args.getClassSkeleton();
                                        List<BuilderUtil.TestMethod> methods = new ArrayList<>();
                                        Object rawMethods = args.getMethods();
                                        if (rawMethods instanceof Map<?, ?> methodsMap) {
                                            for (Map.Entry<?, ?> entry : methodsMap.entrySet()) {
                                                String methodName = Objects.toString(entry.getKey(), null);
                                                String fullImpl = Objects.toString(entry.getValue(), "");
                                                methods.add(new BuilderUtil.TestMethod(methodName, fullImpl));
                                            }
                                        }
                                        // Build and write the test class
                                        newTestSource = BuilderUtil.buildAndWriteTestClass(project, testFile, packageDir, testFileName, classSkeleton, methods, myConsole).stripTrailing();
                                        if(newTestSource != null) actualMessageContentsModel.add(getMessageTextContent(testFileName+" = \n"+newTestSource));
                                        shouldRebuild = true;

                                        // update jaipilot cache.
                                        List<String> files = args.getFilesUsed();
                                        if (cutFqn != null && CollectionUtils.isNotEmpty(files)) {
                                            CacheDTO dto = llmGetFilesCache.get(cutFqn);
                                            if (dto == null) dto = new CacheDTO(cutFqn);
                                            dto.files = new ArrayList<>(files);
                                            llmGetFilesCache.put(cutFqn, dto);
                                        }


                                    } catch (Exception e) {
                                        ConsolePrinter.info(myConsole, "⚠️ Error composing test class: " + e.getMessage());
                                        continue;
                                    }
                                    break;
                                }
                                case "get_file":
                                    String filePath = args.getFilePath();

                                    ConsolePrinter.info(myConsole, "Fetching file details: " + filePath);

//                                    if(isClassPathFetched.contains(filePath) || filePath.endsWith(testFileName) || filePath.endsWith(cutName+".java")) {
//                                        ConsolePrinter.info(myConsole, "Duplicate file - ignoring");
//                                        continue;
//                                    }
                                    isClassPathFetched.add(filePath);
                                    String toolResult = stripCommentsAndMethodBodies(project, filePath);

                                    if (StringUtils.isEmpty(toolResult)) {
                                        ConsolePrinter.info(myConsole, "No matches or file not found: " + filePath);
                                        toolResult = filePath + " not found or no matches found.";
                                    } else {
                                        ConsolePrinter.success(myConsole, "Snippet(s): " + toolResult);
                                        ConsolePrinter.success(myConsole, "Fetched file snippet(s): " + filePath);
                                    }

                                    actualMessageContentsModel.add(getMessageToolRequestContent(toolUseId, fn, args));
                                    messageContentsModel.add(getMessageToolRequestContent(toolUseId, fn, args));
                                    actualMessageContentsUser.add(getMessageToolResultContent(toolUseId, toolResult, false));
                                    messageContentsUser.add(getMessageToolResultContent(toolUseId, toolResult, false));

                                    if (cutFqn != null) {
                                        CacheDTO dto = llmGetFilesCache.get(cutFqn);
                                        if (dto == null) dto = new CacheDTO(cutFqn);

                                        if (dto.files == null) dto.files = new ArrayList<>();
                                        if (!dto.files.contains(filePath)) dto.files.add(filePath);

                                        llmGetFilesCache.put(cutFqn, dto);
                                    }

                                    break;
                                case "terminate_call":
                                    ConsolePrinter.warn(myConsole, "Attempts breached. I have tried my best to compile and execute tests. Please fix the remaining tests manually. ");
                                    Telemetry.allGenError(String.valueOf(attempt), "terminate call");
                                    return;
                            }
                        }
                    }

                    messages.add(JAIPilotLLM.getMessageTool(MODEL_ROLE,messageContentsModel));
                    messages.add(JAIPilotLLM.getMessageTool(USER_ROLE,messageContentsUser));
                    actualMessages.add(JAIPilotLLM.getMessageTool(MODEL_ROLE,actualMessageContentsModel));
                    actualMessages.add(JAIPilotLLM.getMessageTool(USER_ROLE,actualMessageContentsUser));
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
                            """
                            If JAIPilot helped you, please <a href="review">leave a review</a> ⭐️<br><br>
                            Quick feedback: <a href="good">👍</a> &nbsp;&nbsp; <a href="bad">👎</a>
                            """,
                            NotificationType.INFORMATION
                    )
                    .setListener((notification, event) -> {
                        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                            return;
                        }

                        String link = event.getDescription();

                        switch (link) {
                            case "review" -> {
                                BrowserUtil.browse("https://plugins.jetbrains.com/plugin/27706-jaipilot--ai-unit-test-generator/edit/reviews/new");
                                notification.expire();
                            }
                            case "good" -> {
                                sendFeedback(AISettings.getInstance().getProKey(), 5, getAppVersion());
                                showThanks(project);
                                notification.expire();
                            }
                            case "bad" -> {
                                sendFeedback(AISettings.getInstance().getProKey(), 1, getAppVersion());
                                showThanks(project);
                                notification.expire();
                            }
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

    private static void sendFeedback(String usageKey, int rating, String version) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String body = """
                {
                  "usage_key": "%s",
                  "rating": %d,
                  "app_version": "%s"
                }
                """.formatted(usageKey, rating, version);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/give-feedback"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        });
    }

    private static void showThanks(Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("JAIPilot - One-Click AI Agent for Java Unit Testing Feedback")
                .createNotification("Thanks for your feedback!", NotificationType.INFORMATION)
                .notify(project);
    }

    private static @Nullable PsiDirectory resolveTestPackageDir(Project project,
                                                                PsiDirectory testRoot,
                                                                PsiClass cut) throws Exception {
        String relPath = getTestRelativePath(cut);
        return getOrCreateSubdirectoryPath(project, testRoot, relPath);
    }

    private static @Nullable String getTestRelativePath(PsiClass cut) {
        PsiPackage cutPkg = ReadAction.compute(() ->
                JavaDirectoryService.getInstance().getPackage(cut.getContainingFile().getContainingDirectory())
        );
        if (cutPkg == null) return null;
        return ReadAction.compute(() -> cutPkg.getQualifiedName().replace('.', '/'));
    }

    private static @Nullable String getRelativePath(
            PsiClass cut
    ) {
        return ReadAction.compute(() ->JavaDirectoryService.getInstance().getPackage(
                cut.getContainingFile().getContainingDirectory()
        ).getQualifiedName().replace('.', '/'));
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
