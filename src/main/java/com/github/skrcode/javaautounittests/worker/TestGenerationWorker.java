// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.dto.CacheDTO;
import com.github.skrcode.javaautounittests.dto.Message;
import com.github.skrcode.javaautounittests.dto.PromptResponseOutput;
import com.github.skrcode.javaautounittests.dto.QuotaResponse;
import com.github.skrcode.javaautounittests.service.ExecutionManager;
import com.github.skrcode.javaautounittests.service.GenerateTestsLLMService;
import com.github.skrcode.javaautounittests.service.QuotaService;
import com.github.skrcode.javaautounittests.state.GenerateTestsGetFilesCache;
import com.github.skrcode.javaautounittests.util.BuilderUtil;
import com.github.skrcode.javaautounittests.util.CUTUtil;
import com.github.skrcode.javaautounittests.util.ConsolePrinter;
import com.github.skrcode.javaautounittests.util.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.github.skrcode.javaautounittests.service.GenerateTestsLLMService.*;
import static com.github.skrcode.javaautounittests.service.ReviewService.showReviewAfterTestGeneration;
import static com.github.skrcode.javaautounittests.util.CUTUtil.resolveTestPackageDir;
import static com.github.skrcode.javaautounittests.util.CUTUtil.stripCommentsAndMethodBodies;

public final class TestGenerationWorker {

    private static final int MAX_ATTEMPTS = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void process(Project project, PsiClass cut, @NotNull ConsoleView myConsole, PsiDirectory testRoot, @NotNull ProgressIndicator indicator) {
        int attempt = 1;
        try {
            try {
                QuotaResponse quotaResponse = QuotaService.fetchQuota();
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

            GenerateTestsGetFilesCache generateTestsGetFilesCache = GenerateTestsGetFilesCache.getInstance(project);
            CacheDTO generateTestsGetFilesCacheEntry = generateTestsGetFilesCache.get(cutFqn);
            if (generateTestsGetFilesCacheEntry != null && generateTestsGetFilesCacheEntry.files != null) {
                cachedPaths = generateTestsGetFilesCacheEntry.files;
            }

            Telemetry.allGenBegin(testFileName);

            boolean isLLMGeneratedAtLeastOnce = false;

            ExecutionManager.reset();
            List<Message> messages = new ArrayList<>();
            String cutSource = CUTUtil.cleanedSourceForLLM(project, cut);
            messages.add(GenerateTestsLLMService.getMessage(GenerateTestsLLMService.USER_ROLE,testFileName));
            messages.add(GenerateTestsLLMService.getMessage(GenerateTestsLLMService.USER_ROLE,cutSource)); // get cut tool
            messages.add(GenerateTestsLLMService.getMessage(GenerateTestsLLMService.USER_ROLE,"Mockito version = "+CUTUtil.findMockitoVersion(project)));
            // Add existing test class (if present) as context
            Ref<PsiFile> testFileExisting = ReadAction.compute(() -> Ref.create(packageDir.findFile(testFileName)));
            String existingTestSource = "";
            if (ReadAction.compute(testFileExisting::get) != null) {
                existingTestSource = ReadAction.compute(() -> testFileExisting.get().getText());
            }
            messages.add(GenerateTestsLLMService.getMessage(USER_ROLE,testFileName+" = \n"+existingTestSource));

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
                    messages.add(GenerateTestsLLMService.getMessage(USER_ROLE,cachedFilePath +"=\n"+cachedFileContent));
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
                            actualMessages.add(GenerateTestsLLMService.getMessage(USER_ROLE,errorOutput));
                        }
                        else {
                            ConsolePrinter.success(myConsole, "Tests execution successful " + testFileName);
                            if(isLLMGeneratedAtLeastOnce) break;
                        }
                    }
                    else {
                        ConsolePrinter.info(myConsole, "Found compilation errors " + testFileName);
                        actualMessages.add(GenerateTestsLLMService.getMessage(USER_ROLE,errorOutput));
                    }
                }
                shouldRebuild = false;
                if (attempt > MAX_ATTEMPTS) {
                    ConsolePrinter.warn(myConsole, "Attempts breached. I have tried my best to compile and execute tests. Please fix the remaining tests manually. " + testFileName);
                    break;
                }

                if(attempt == 1) {
                    PromptResponseOutput planOutput = GenerateTestsLLMService.generatePlan(
                            testFileName,
                            actualMessages,
                            myConsole,
                            attempt,
                            indicator
                    );
                    Message.MessageContent messageContent = MAPPER.convertValue(planOutput.getMessage().getContentAsList().get(0),Message.MessageContent.class);
                    String testPlan = messageContent.getText();
                    ConsolePrinter.info(myConsole, "Fetching test plan: \n" + testPlan);
                    messages.add(GenerateTestsLLMService.getMessage(USER_ROLE,testPlan, true));
                    actualMessages.add(GenerateTestsLLMService.getMessage(USER_ROLE,testPlan, true));
                }

                ConsolePrinter.info(myConsole, "Generating tests " + testFileName +" Please wait....");
                indicator.checkCanceled();

                PromptResponseOutput output = GenerateTestsLLMService.generateContent(
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
                                        newTestSource = BuilderUtil.buildAndWriteTestClass(project, testFile, packageDir, testFileName, classSkeleton, methods, myConsole);
                                        if(newTestSource != null) actualMessageContentsModel.add(getMessageTextContent(testFileName+" = \n"+newTestSource.stripTrailing()));
                                        shouldRebuild = true;

                                        // update jaipilot cache.
                                        List<String> files = args.getFilesUsed();
                                        if (CollectionUtils.isNotEmpty(files)) {
                                            if (generateTestsGetFilesCacheEntry == null) generateTestsGetFilesCacheEntry = new CacheDTO(cutFqn);
                                            generateTestsGetFilesCacheEntry.files = new ArrayList<>(files);
                                            generateTestsGetFilesCache.put(cutFqn, generateTestsGetFilesCacheEntry);
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

                                    if (generateTestsGetFilesCacheEntry == null) generateTestsGetFilesCacheEntry = new CacheDTO(cutFqn);
                                    if (generateTestsGetFilesCacheEntry.files == null) generateTestsGetFilesCacheEntry.files = new ArrayList<>();
                                    if (!generateTestsGetFilesCacheEntry.files.contains(filePath)) generateTestsGetFilesCacheEntry.files.add(filePath);
                                    generateTestsGetFilesCache.put(cutFqn, generateTestsGetFilesCacheEntry);
                                    break;
                                case "terminate_call":
                                    ConsolePrinter.warn(myConsole, "Attempts breached. I have tried my best to compile and execute tests. Please fix the remaining tests manually. ");
                                    Telemetry.allGenError(String.valueOf(attempt), "terminate call");
                                    return;
                            }
                        }
                    }

                    if(CollectionUtils.isNotEmpty(messageContentsModel)) messages.add(GenerateTestsLLMService.getMessageTool(MODEL_ROLE,messageContentsModel));
                    if(CollectionUtils.isNotEmpty(messageContentsUser)) messages.add(GenerateTestsLLMService.getMessageTool(USER_ROLE,messageContentsUser));
                    if(CollectionUtils.isNotEmpty(actualMessageContentsModel)) actualMessages.add(GenerateTestsLLMService.getMessageTool(MODEL_ROLE,actualMessageContentsModel));
                    if(CollectionUtils.isNotEmpty(actualMessageContentsUser)) actualMessages.add(GenerateTestsLLMService.getMessageTool(USER_ROLE,actualMessageContentsUser));
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
            showReviewAfterTestGeneration(project);

        } catch (Throwable t) {
            Telemetry.allGenError(String.valueOf(attempt), t.getMessage());
            ConsolePrinter.error(myConsole, "Generation failed: " + t.getMessage());

            t.printStackTrace();
            ApplicationManager.getApplication().invokeLater(() ->
                    Messages.showErrorDialog(t.getMessage(), "Error. Please retry in a few minutes.")
            );
        }
    }

    private TestGenerationWorker() {}
}
