// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.GenerationType;
import com.github.skrcode.javaautounittests.dto.*;
import com.github.skrcode.javaautounittests.service.ExecutionManager;
import com.github.skrcode.javaautounittests.service.GenerateTestsLLMService;
import com.github.skrcode.javaautounittests.state.GenerateTestsGetFilesCache;
import com.github.skrcode.javaautounittests.util.CUTUtil;
import com.github.skrcode.javaautounittests.util.ConsolePrinter;
import com.github.skrcode.javaautounittests.util.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.skrcode.javaautounittests.service.GenerateTestsLLMService.MODEL_ROLE;
import static com.github.skrcode.javaautounittests.service.GenerateTestsLLMService.USER_ROLE;
import static com.github.skrcode.javaautounittests.service.QuotaService.printQuotaWarning;
import static com.github.skrcode.javaautounittests.service.ReviewService.showReviewAfterTestGeneration;
import static com.github.skrcode.javaautounittests.util.BuilderUtil.buildAndRun;
import static com.github.skrcode.javaautounittests.util.CUTUtil.testFileExists;
import static com.github.skrcode.javaautounittests.util.GetFilesCacheUtil.getCachedGetFilesCachedPaths;
import static com.github.skrcode.javaautounittests.util.GetFilesCacheUtil.getCachedGetFilesMessages;
import static com.github.skrcode.javaautounittests.util.Telemetry.getCombinedClassName;
import static com.github.skrcode.javaautounittests.util.ToolHandlerUtil.handleApplyTestClass;
import static com.github.skrcode.javaautounittests.util.ToolHandlerUtil.handleGetFile;

public final class TestGenerationWorker {

    private static final int MAX_ATTEMPTS = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void process(Project project, List<PsiClass> cuts, @NotNull ConsoleView myConsole, @NotNull ProgressIndicator indicator, GenerationType generationType) {
        int attempt = 1;
        try {
            printQuotaWarning(myConsole);
            long start = System.nanoTime();
            List<FileInfo> cutFileInfos = cuts.stream()
                    .map(CUTUtil::getCutFileInfo)
                    .toList();
            List<FileInfo> testFileInfos = cutFileInfos.stream()
                    .map(cutFileInfo ->
                            generationType.equals(GenerationType.generate)
                                    ? CUTUtil.getOrCreateTestFile(project, cutFileInfo)
                                    : cutFileInfo
                    )
                    .toList();
            GenerateTestsGetFilesCache generateTestsGetFilesCache = GenerateTestsGetFilesCache.getInstance(project);
            Telemetry.allGenBegin(getCombinedClassName(testFileInfos));
            boolean isLLMGeneratedAtLeastOnce = false;
            ExecutionManager.reset();
            List<MessagesRequestDTO> messagesRequestDTOs = new ArrayList<>();
            List<String> cutSources = cuts.stream()
                    .map(cut -> CUTUtil.cleanedSourceForLLM(project, cut))
                    .toList();
            for(int i=0;i<cuts.size();i++) {
                MessagesRequestDTO messagesRequestDTO = new MessagesRequestDTO();
                messagesRequestDTO
                        .addMessage((GenerateTestsLLMService.getMessage(GenerateTestsLLMService.USER_ROLE, testFileInfos.get(i).simpleName())))
                        .addMessage(GenerateTestsLLMService.getMessage(GenerateTestsLLMService.USER_ROLE, cutSources.get(i)))
                        .addMessage(GenerateTestsLLMService.getMessage(GenerateTestsLLMService.USER_ROLE,"Mockito version = "+CUTUtil.findMockitoVersion(project)));
                if(generationType.equals(GenerationType.generate))
                    messagesRequestDTO.addMessage(GenerateTestsLLMService.getMessage(USER_ROLE, testFileInfos.get(i).simpleName()+" = \n"+testFileInfos.get(i).source()));
                Set<String> isClassPathFetched = new HashSet<>();
                messagesRequestDTO.addAllMessages(getCachedGetFilesMessages(getCachedGetFilesCachedPaths(generateTestsGetFilesCache, cutFileInfos.get(i).qualifiedName()), myConsole,project, testFileInfos.get(i).filePath(), cutFileInfos.get(i).filePath(), isClassPathFetched));
                messagesRequestDTO.setActualMessages(messagesRequestDTO.getMessages());
                messagesRequestDTOs.add(messagesRequestDTO);
            }
            boolean shouldRebuild = true;
            String newTestSource = null;
            for (; ; attempt++) {
                ConsolePrinter.section(myConsole, "Attempting");
                boolean isFileCorrupted = false;
                for(int i=0;i<testFileInfos.size();i++) {
                    testFileInfos.set(i, CUTUtil.getOrCreateTestFile(project, cutFileInfos.get(i)));
                    if (!testFileExists(testFileInfos.get(i).psiFile())) {
                        ConsolePrinter.warn(myConsole, "File corrupted during run. Please retry. " + testFileInfos.get(i).simpleName());
                        isFileCorrupted = true;
                    }
                }
                if(isFileCorrupted) break;
                // send all here in one shot
                if(shouldRebuild && buildAndRun(project, myConsole, indicator, messagesRequestDTO, testFileInfo.psiFile(), testFileInfo.simpleName()) && isLLMGeneratedAtLeastOnce) break;
                shouldRebuild = false;
                if (attempt > MAX_ATTEMPTS) {
                    ConsolePrinter.warn(myConsole, "Attempts breached. I have tried my best to compile and execute tests. Please fix the remaining tests manually. " + testFileInfo.simpleName());
                    break;
                }
                if(attempt == 1 && generationType.equals(GenerationType.generate)) {
                    // send all here in one shot and get back list of plans
                    PromptResponseOutput planOutput = GenerateTestsLLMService.generatePlan(getCombinedClassName(testFileInfos), messagesRequestDTO.getActualMessages(),myConsole, attempt, indicator);
                    Message.MessageContent messageContent = MAPPER.convertValue(planOutput.getMessage().getContentAsList().get(0),Message.MessageContent.class);
                    ConsolePrinter.info(myConsole, "Fetching test plan: \n" + messageContent.getText());
                    messagesRequestDTO.addToBoth(GenerateTestsLLMService.getMessage(USER_ROLE,messageContent.getText(), true));
                }
                ConsolePrinter.info(myConsole, "Generating tests " + getCombinedClassName(testFileInfos) +" Please wait....");
                indicator.checkCanceled();
                PromptResponseOutput output = GenerateTestsLLMService.generateContent(testFileInfo.simpleName(), messagesRequestDTO.getActualMessages(), myConsole, attempt, indicator, generationType);
                messagesRequestDTO.setActualMessages(messagesRequestDTO.getMessages());
                MessagesContentsRequestDTO messagesContentsRequestDTO = new MessagesContentsRequestDTO();
                for (Object messageContentObject: output.getMessage().getContentAsList()) {
                    Message.MessageContent messageContent = MAPPER.convertValue(messageContentObject,Message.MessageContent.class);
                    if (messageContent.getType().equals("thinking") || messageContent.getType().equals("redacted_thinking")) messagesContentsRequestDTO.addToBothModel(messageContent);
                    if (!messageContent.getType().equals("tool_use")) continue;
                    Message.MessageContent.Input args = messageContent.getInput();
                    if(messageContent.getName().equals("apply_test_class")) {
                        isLLMGeneratedAtLeastOnce = true;
                        indicator.checkCanceled();
                        newTestSource = handleApplyTestClass(project,myConsole,args,generateTestsGetFilesCache, cutFileInfo.qualifiedName(), newTestSource,messagesContentsRequestDTO, testFileInfo);
                        shouldRebuild = true;
                    }
                    if(messageContent.getName().equals("get_file")) handleGetFile(project,myConsole,args, messageContent.getId(), testFileInfo.filePath(), cutFileInfo.filePath(),  isClassPathFetched, messagesContentsRequestDTO, generateTestsGetFilesCache, cutFileInfo.qualifiedName(), messageContent.getName());
                    if(messageContent.getName().equals("terminate_call")) {
                        ConsolePrinter.warn(myConsole, "Attempts breached. I have tried my best to compile and execute tests. Please fix the remaining tests manually. ");
                        Telemetry.allGenError(String.valueOf(attempt), "terminate call");
                    }
                }
                messagesRequestDTO
                        .addMessage(GenerateTestsLLMService.getMessageTool(MODEL_ROLE,messagesContentsRequestDTO.getMessageContentsModel())).addMessage(GenerateTestsLLMService.getMessageTool(USER_ROLE,messagesContentsRequestDTO.getMessageContentsUser()))
                        .addActualMessage(GenerateTestsLLMService.getMessageTool(MODEL_ROLE,messagesContentsRequestDTO.getActualMessageContentsModel())).addActualMessage(GenerateTestsLLMService.getMessageTool(USER_ROLE,messagesContentsRequestDTO.getActualMessageContentsUser()));
            }
            Telemetry.allGenDone(getCombinedClassName(testFileInfos), String.valueOf(attempt), (System.nanoTime() - start) / 1_000_000);
            ConsolePrinter.section(myConsole, "Summary");
            ConsolePrinter.success(myConsole, "Successfully generated Test Class " + getCombinedClassName(testFileInfos));
            showReviewAfterTestGeneration(project);

        } catch (Throwable t) {
            Telemetry.allGenError(String.valueOf(attempt), t.getMessage());
            ConsolePrinter.error(myConsole, "Generation failed: " + t.getMessage());
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(t.getMessage(), "Error. Please retry in a few minutes."));
        }
    }

    private TestGenerationWorker() {}
}
