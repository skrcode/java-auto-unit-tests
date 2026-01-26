// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.constants.GenerationType;
import com.github.skrcode.javaautounittests.dto.*;
import com.github.skrcode.javaautounittests.service.ExecutionManager;
import com.github.skrcode.javaautounittests.service.GenerateTestsLLMService;
import com.github.skrcode.javaautounittests.state.GenerateTestsGetFilesCache;
import com.github.skrcode.javaautounittests.util.BuilderUtil;
import com.github.skrcode.javaautounittests.util.CUTUtil;
import com.github.skrcode.javaautounittests.util.ConsolePrinter;
import com.github.skrcode.javaautounittests.util.Telemetry;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.github.skrcode.javaautounittests.service.GenerateTestsLLMService.MODEL_ROLE;
import static com.github.skrcode.javaautounittests.service.GenerateTestsLLMService.USER_ROLE;
import static com.github.skrcode.javaautounittests.service.QuotaService.printQuotaWarning;
import static com.github.skrcode.javaautounittests.service.ReviewService.showReviewAfterTestGeneration;
import static com.github.skrcode.javaautounittests.util.CUTUtil.testFileExists;
import static com.github.skrcode.javaautounittests.util.GetFilesCacheUtil.getCachedGetFilesCachedPaths;
import static com.github.skrcode.javaautounittests.util.GetFilesCacheUtil.getCachedGetFilesMessages;
import static com.github.skrcode.javaautounittests.util.LLMMessageContentUtil.getMessage;
import static com.github.skrcode.javaautounittests.util.LLMMessageContentUtil.getMessageTool;
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
                    .filter(Objects::nonNull)
                    .toList();
            List<FileInfo> testFileInfos = new ArrayList<>(cutFileInfos.size());
            for (FileInfo cutFileInfo : cutFileInfos) {
                FileInfo testFileInfo = generationType.equals(GenerationType.generate)
                        ? CUTUtil.getOrCreateTestFile(project, cutFileInfo)
                        : cutFileInfo;
                testFileInfos.add(testFileInfo);
            }
            GenerateTestsGetFilesCache generateTestsGetFilesCache = GenerateTestsGetFilesCache.getInstance(project);
            Telemetry.allGenBegin(getCombinedClassName(testFileInfos));
            ExecutionManager.reset();
            List<MessagesRequestDTO> messagesRequestDTOs = new ArrayList<>();
            List<Set<String>> classPathFetchedFlags = new ArrayList<>();
            List<String> cutSources = cutFileInfos.stream().map(cut -> CUTUtil.cleanedSourceForLLM(project, cut.cutClass())).toList();
            List<ClassGenerationMetadataStateDTO> state = new ArrayList<>();
            for(int i=0;i<cutFileInfos.size();i++) {
                MessagesRequestDTO messagesRequestDTO = new MessagesRequestDTO();
                messagesRequestDTO
                        .addMessage((getMessage(GenerateTestsLLMService.USER_ROLE, testFileInfos.get(i).simpleName())))
                        .addMessage(getMessage(GenerateTestsLLMService.USER_ROLE, cutSources.get(i)))
                        .addMessage(getMessage(GenerateTestsLLMService.USER_ROLE,"Mockito version = "+CUTUtil.findMockitoVersion(project)));
                if(generationType.equals(GenerationType.generate))
                    messagesRequestDTO.addMessage(getMessage(USER_ROLE, testFileInfos.get(i).simpleName()+" = \n"+testFileInfos.get(i).source()));
                Set<String> isClassPathFetched = new HashSet<>();
                messagesRequestDTO.addAllMessages(getCachedGetFilesMessages(getCachedGetFilesCachedPaths(generateTestsGetFilesCache, cutFileInfos.get(i).qualifiedName()), myConsole,project, testFileInfos.get(i).filePath(), cutFileInfos.get(i).filePath(), isClassPathFetched));
                messagesRequestDTO.setActualMessages(messagesRequestDTO.getMessages());
                messagesRequestDTOs.add(messagesRequestDTO);
                classPathFetchedFlags.add(isClassPathFetched);
                state.add(new ClassGenerationMetadataStateDTO());
            }

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
                if (attempt > MAX_ATTEMPTS) {
                    ConsolePrinter.warn(myConsole, "Maximum attempts reached. JAIPilot has made multiple attempts to generate, compile, and execute the tests but could not make further progress. Please review and fix any remaining issues manually. " + getCombinedClassName(testFileInfos));
                    break;
                }
                // build all classes
                ConsolePrinter.info(myConsole, "Compiling Tests ");
                StringBuilder failedClassNamesBuilder = new StringBuilder();
                indicator.checkCanceled();
                boolean isAllSuccess = true;
                for(int i=0;i<testFileInfos.size();i++) {
                    if(!state.get(i).isShouldRebuild()) {
                        if(!state.get(i).isBuildSuccess())failedClassNamesBuilder.append(testFileInfos.get(i).simpleName());
                        isAllSuccess = isAllSuccess && state.get(i).isBuildSuccess();
                        continue;
                    }
                    PsiJavaFile testFile = testFileInfos.get(i).psiFile();
                    if(testFile.getClasses().length == 0) continue; // class not found but file present
                    String errorOutput = BuilderUtil.compileJUnitClass(project, testFile);
                    MessagesRequestDTO messagesRequestDTO = messagesRequestDTOs.get(i);
                    if (!errorOutput.isEmpty()) {
                        ConsolePrinter.info(myConsole, "Found compilation errors " + testFileInfos.get(i).simpleName());
                        failedClassNamesBuilder.append(testFile.getName());
                        messagesRequestDTO.addActualMessage(getMessage(USER_ROLE,errorOutput));
                        isAllSuccess = false;
                        state.get(i).setReadyToGeneratePlan(true);
                        state.get(i).setShouldRebuild(false);
                        state.get(i).setBuildSuccess(false);
                    }
                    else state.get(i).setBuildSuccess(true);
                }
                // execute all classes
                if(isAllSuccess) {
                    ConsolePrinter.success(myConsole, "Compilation Successful");
                    ConsolePrinter.info(myConsole, "Running Tests");
                    indicator.checkCanceled();
                    for (int i = 0; i < testFileInfos.size(); i++) {
                        if (!state.get(i).isShouldRebuild()) {
                            if(!state.get(i).isBuildAndRunSuccess())failedClassNamesBuilder.append(testFileInfos.get(i).simpleName());
                            isAllSuccess = isAllSuccess && state.get(i).isBuildAndRunSuccess();
                            continue;
                        }
                        PsiJavaFile testFile = testFileInfos.get(i).psiFile();
                        if (testFile.getClasses().length == 0) {
                            state.get(i).setBuildAndRunSuccess(false);
                            state.get(i).setReadyToGeneratePlan(true);
                            state.get(i).setShouldRebuild(false);
                            continue; // class not found but file present
                        }
                        String errorOutput = BuilderUtil.runJUnitClass(project, testFile);
                        MessagesRequestDTO messagesRequestDTO = messagesRequestDTOs.get(i);
                        if (!errorOutput.isEmpty()) {
                            ConsolePrinter.info(myConsole, "Found tests execution errors " + testFile.getName());
                            failedClassNamesBuilder.append(testFile.getName());
                            messagesRequestDTO.addActualMessage(getMessage(USER_ROLE, errorOutput));
                            isAllSuccess = false;
                            state.get(i).setBuildAndRunSuccess(false);
                        }
                        else state.get(i).setBuildAndRunSuccess(true);
                        state.get(i).setReadyToGeneratePlan(true);
                        state.get(i).setShouldRebuild(false);
                    }
                    if (isAllSuccess) {
                        ConsolePrinter.success(myConsole, "Tests execution successful");
                        if(generationType.equals(GenerationType.fix)) break;
                        boolean isLLMGeneratedAtLeastOnceForEveryClass = true;
                        for(int i = 0;i<state.size();i++)
                            isLLMGeneratedAtLeastOnceForEveryClass = isLLMGeneratedAtLeastOnceForEveryClass && state.get(i).isLLMGeneratedAtleastOnce();
                        if(isLLMGeneratedAtLeastOnceForEveryClass) break;
                    }
                    else ConsolePrinter.info(myConsole, "Found tests execution errors " + failedClassNamesBuilder);
                }
                else ConsolePrinter.info(myConsole, "Found compilation errors " + failedClassNamesBuilder);

                ConsolePrinter.info(myConsole, "Generating tests " + getCombinedClassName(testFileInfos) +". Please wait....");
                indicator.checkCanceled();
                if(generationType.equals(GenerationType.generate)) { // only for plan generation
                    List<List<Message>> messageRequestsToLLM = new ArrayList<>();
                    for(int i=0;i<state.size();i++) {
                        if(!state.get(i).isPlanGenerated() && state.get(i).isReadyToGeneratePlan()) messageRequestsToLLM.add(messagesRequestDTOs.get(i).getActualMessages());
                        else messageRequestsToLLM.add(null);
                    }
                    if(!CollectionUtils.isEmpty(messageRequestsToLLM)) {
                        List<Message> planOutputMessages = GenerateTestsLLMService.generate(getCombinedClassName(testFileInfos), messageRequestsToLLM, myConsole, attempt, indicator, null);
                        for (int i = 0; i < planOutputMessages.size(); i++) {
                            Message message = planOutputMessages.get(i);
                            if (message == null) continue;
                            Message.MessageContent messageContent = MAPPER.convertValue(message.getContentAsList().get(0), Message.MessageContent.class);
                            ConsolePrinter.info(myConsole, "Fetching test plan: \n" + messageContent.getText());
                            messagesRequestDTOs.get(i).addToBoth(getMessage(USER_ROLE, messageContent.getText(), true));
                            state.get(i).setPlanGenerated(true);
                        }
                    }
                }

                // actual generation
                List<List<Message>> messageRequestsToLLM = new ArrayList<>();
                for(int i=0;i<state.size();i++) {
                    if(generationType.equals(GenerationType.generate) && state.get(i).isLLMGeneratedAtleastOnce() && state.get(i).isBuildAndRunSuccess()) messageRequestsToLLM.add(null);
                    else if(generationType.equals(GenerationType.fix) && state.get(i).isBuildAndRunSuccess()) messageRequestsToLLM.add(null);
                    else messageRequestsToLLM.add(messagesRequestDTOs.get(i).getActualMessages());
                }
                List<Message> outputMessages = GenerateTestsLLMService.generate(getCombinedClassName(testFileInfos),messageRequestsToLLM,myConsole, attempt, indicator, generationType);
                for(MessagesRequestDTO messagesRequestDTO:messagesRequestDTOs)messagesRequestDTO.setActualMessages(messagesRequestDTO.getMessages()); // initialize

                for(int i=0;i<outputMessages.size();i++) {
                    Message message = outputMessages.get(i);
                    MessagesContentsRequestDTO messagesContentsRequestDTO = new MessagesContentsRequestDTO();
                    if(message == null) continue; // skipped message
                    for (Object messageContentObject : message.getContentAsList()) {
                        Message.MessageContent messageContent = MAPPER.convertValue(messageContentObject, Message.MessageContent.class);
                        if (messageContent.getType().equals("thinking") || messageContent.getType().equals("redacted_thinking"))
                            messagesContentsRequestDTO.addToBothModel(messageContent);
                        if (!messageContent.getType().equals("tool_use")) continue;
                        Message.MessageContent.Input args = messageContent.getInput();
                        if (messageContent.getName().equals("apply_test_class")) {
                            indicator.checkCanceled();
                            state.get(i).setNewTestSource(handleApplyTestClass(project, myConsole, args, generateTestsGetFilesCache, cutFileInfos.get(i).qualifiedName(), state.get(i).getNewTestSource(), messagesContentsRequestDTO, testFileInfos.get(i)));
                            state.get(i).setLLMGeneratedAtleastOnce(true);
                            state.get(i).setShouldRebuild(true);
                        }
                        if (messageContent.getName().equals("get_file"))
                            handleGetFile(project, myConsole, args, messageContent.getId(), testFileInfos.get(i).filePath(), cutFileInfos.get(i).filePath(), classPathFetchedFlags.get(i), messagesContentsRequestDTO, generateTestsGetFilesCache, cutFileInfos.get(i).qualifiedName(), messageContent.getName());
                        if (messageContent.getName().equals("terminate_call")) {
                            ConsolePrinter.warn(myConsole, "Maximum attempts reached. JAIPilot has made multiple attempts to generate, compile, and execute the tests but could not make further progress. Please review and fix any remaining issues manually. ");
                            Telemetry.allGenError(String.valueOf(attempt), "terminate call");
                        }
                    }
                    messagesRequestDTOs.get(i)
                            .addMessage(getMessageTool(MODEL_ROLE, messagesContentsRequestDTO.getMessageContentsModel())).addMessage(getMessageTool(USER_ROLE, messagesContentsRequestDTO.getMessageContentsUser()))
                            .addActualMessage(getMessageTool(MODEL_ROLE, messagesContentsRequestDTO.getActualMessageContentsModel())).addActualMessage(getMessageTool(USER_ROLE, messagesContentsRequestDTO.getActualMessageContentsUser()));
                }
            }
            Telemetry.allGenDone(getCombinedClassName(testFileInfos), String.valueOf(attempt), (System.nanoTime() - start) / 1_000_000);
            ConsolePrinter.section(myConsole, "Summary");
            ConsolePrinter.success(myConsole, "Successfully generated Test Class " + getCombinedClassName(testFileInfos));
            showReviewAfterTestGeneration(project);

        } catch (Throwable t) {
            Telemetry.allGenError(String.valueOf(attempt), t.getMessage());
            ConsolePrinter.error(myConsole, "Generation failed: Please retry in a few minutes.");
            ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(t.getMessage(), "Error. Please retry in a few minutes."));
        }
    }

    private TestGenerationWorker() {}
}
