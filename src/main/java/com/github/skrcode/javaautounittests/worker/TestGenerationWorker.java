// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.constants.GenerationType;
import com.github.skrcode.javaautounittests.constants.TransitionStateClass;
import com.github.skrcode.javaautounittests.constants.TransitionStateClassAll;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.github.skrcode.javaautounittests.constants.TransitionStateClassAll.*;
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

            // initial build
            TransitionStateClassAll allState = INITIAL;
            ConsolePrinter.section(myConsole, "Initial checks");
            for(int i=0;i<testFileInfos.size();i++) {
                testFileInfos.set(i, CUTUtil.getOrCreateTestFile(project, cutFileInfos.get(i)));
                if (!testFileExists(testFileInfos.get(i).psiFile())) {
                    ConsolePrinter.warn(myConsole, "File corrupted during run. Please retry. " + testFileInfos.get(i).simpleName());
                    allState = TransitionStateClassAll.CORRUPTED;
                }
            }
            if(allState.equals(TransitionStateClassAll.CORRUPTED)) throw new Exception("File Corrupted");
            ConsolePrinter.info(myConsole, "Compiling Tests ");
            StringBuilder failedClassNamesBuilder = new StringBuilder();
            indicator.checkCanceled();
            allState = TransitionStateClassAll.INITIAL_ALL_BUILD_SUCCESS;
            for(int i=0;i<testFileInfos.size();i++) {
                PsiJavaFile testFile = testFileInfos.get(i).psiFile();
                if(testFile.getClasses().length == 0) {
                    state.get(i).setCurrentState(TransitionStateClass.INITIAL_BUILD_FAILURE);
                    allState = TransitionStateClassAll.INITIAL_ALL_BUILD_FAILURE;
                    continue; // class not found but file present
                }
                String errorOutput = BuilderUtil.compileJUnitClass(project, testFile);
                MessagesRequestDTO messagesRequestDTO = messagesRequestDTOs.get(i);
                if (!errorOutput.isEmpty()) {
                    ConsolePrinter.info(myConsole, "Found compilation errors " + testFileInfos.get(i).simpleName());
                    failedClassNamesBuilder.append(testFile.getName());
                    messagesRequestDTO.addActualMessage(getMessage(USER_ROLE,errorOutput));
                    allState = TransitionStateClassAll.INITIAL_ALL_BUILD_FAILURE;
                    state.get(i).setCurrentState(TransitionStateClass.INITIAL_BUILD_FAILURE);
                }
                else state.get(i).setCurrentState(TransitionStateClass.INITIAL_BUILD_SUCCESS);
            }
            // execute all classes
            if(allState.equals(TransitionStateClassAll.INITIAL_ALL_BUILD_SUCCESS)) {
                ConsolePrinter.success(myConsole, "Compilation Successful. Running Tests");
                indicator.checkCanceled();
                allState = TransitionStateClassAll.INITIAL_ALL_EXECUTION_SUCCESS;
                for (int i = 0; i < testFileInfos.size(); i++) {
                    PsiJavaFile testFile = testFileInfos.get(i).psiFile();
                    if (testFile.getClasses().length == 0) {
                        state.get(i).setCurrentState(TransitionStateClass.INITIAL_EXECUTION_FAILURE);
                        allState = TransitionStateClassAll.INITIAL_ALL_EXECUTION_FAILURE;
                        continue; // class not found but file present
                    }
                    String errorOutput = BuilderUtil.runJUnitClass(project, testFile);
                    MessagesRequestDTO messagesRequestDTO = messagesRequestDTOs.get(i);
                    if (!errorOutput.isEmpty()) {
                        ConsolePrinter.info(myConsole, "Found tests execution errors " + testFile.getName());
                        failedClassNamesBuilder.append(testFile.getName());
                        messagesRequestDTO.addActualMessage(getMessage(USER_ROLE, errorOutput));
                        allState = TransitionStateClassAll.INITIAL_ALL_EXECUTION_FAILURE;
                        state.get(i).setCurrentState(TransitionStateClass.INITIAL_EXECUTION_FAILURE);
                    }
                    else state.get(i).setCurrentState(TransitionStateClass.INITIAL_EXECUTION_SUCCESS);
                }
                if (allState.equals(TransitionStateClassAll.INITIAL_ALL_EXECUTION_SUCCESS)) ConsolePrinter.success(myConsole, "Tests execution successful");
                else ConsolePrinter.info(myConsole, "Found tests execution errors " + failedClassNamesBuilder);
            }
            else ConsolePrinter.info(myConsole, "Found compilation errors " + failedClassNamesBuilder);

            if (generationType.equals(GenerationType.generate)) { // only for plan generation
                List<List<Message>> messageRequestsToLLM = new ArrayList<>();
                for (int i = 0; i < testFileInfos.size(); i++) messageRequestsToLLM.add(messagesRequestDTOs.get(i).getActualMessages());
                ConsolePrinter.info(myConsole, "Fetching test plan....");
                List<Message> planOutputMessages = GenerateTestsLLMService.generate(getCombinedClassName(testFileInfos), messageRequestsToLLM, myConsole, attempt, indicator, null);
                for (int i = 0; i < planOutputMessages.size(); i++) {
                    Message message = planOutputMessages.get(i);
                    if (message == null) continue;
                    Message.MessageContent messageContent = MAPPER.convertValue(message.getContentAsList().get(0), Message.MessageContent.class);
                    ConsolePrinter.info(myConsole, "Fetched test plan: "+testFileInfos.get(i).simpleName() + "\n" + messageContent.getText());
                    messagesRequestDTOs.get(i).addToBoth(getMessage(USER_ROLE, messageContent.getText(), true));
                }
            }

            if(generationType.equals(GenerationType.generate) || allState.equals(TransitionStateClassAll.INITIAL_ALL_BUILD_FAILURE) || allState.equals(TransitionStateClassAll.INITIAL_ALL_EXECUTION_FAILURE)) {
                for (; ; attempt++) {
                    ConsolePrinter.section(myConsole, "Attempting");
                    ConsolePrinter.info(myConsole, "Generating tests " + getCombinedClassName(testFileInfos) + ". Please wait....");
                    indicator.checkCanceled();

                    boolean isFileCorrupted = false;
                    for (int i = 0; i < testFileInfos.size(); i++) {
                        testFileInfos.set(i, CUTUtil.getOrCreateTestFile(project, cutFileInfos.get(i)));
                        if (!testFileExists(testFileInfos.get(i).psiFile())) {
                            ConsolePrinter.warn(myConsole, "File corrupted during run. Please retry. " + testFileInfos.get(i).simpleName());
                            isFileCorrupted = true;
                        }
                    }
                    if (isFileCorrupted) break;
                    if (attempt > MAX_ATTEMPTS) {
                        ConsolePrinter.warn(myConsole, "Maximum attempts reached. JAIPilot has made multiple attempts to generate, compile, and execute the tests but could not make further progress. Please review and fix any remaining issues manually. " + getCombinedClassName(testFileInfos));
                        break;
                    }

                    // actual generation
                    List<List<Message>> messageRequestsToLLM = new ArrayList<>();
                    for (int i = 0; i < state.size(); i++) {
                        if (state.get(i).shouldGenerateTool(generationType)) {
                            messageRequestsToLLM.add(messagesRequestDTOs.get(i).getActualMessages());
                        }
                        else messageRequestsToLLM.add(null);
                    }
                    List<Message> outputMessages = GenerateTestsLLMService.generate(getCombinedClassName(testFileInfos), messageRequestsToLLM, myConsole, attempt, indicator, generationType);
                    for (MessagesRequestDTO messagesRequestDTO : messagesRequestDTOs) messagesRequestDTO.setActualMessages(messagesRequestDTO.getMessages()); // initialize
                    for (int i = 0; i < outputMessages.size(); i++) {
                        Message message = outputMessages.get(i);
                        MessagesContentsRequestDTO messagesContentsRequestDTO = new MessagesContentsRequestDTO();
                        if (message == null) continue; // skipped message
                        for (Object messageContentObject : message.getContentAsList()) {
                            Message.MessageContent messageContent = MAPPER.convertValue(messageContentObject, Message.MessageContent.class);
                            if (messageContent.getType().equals("thinking") || messageContent.getType().equals("redacted_thinking"))
                                messagesContentsRequestDTO.addToBothModel(messageContent);
                            if (!messageContent.getType().equals("tool_use")) continue;
                            Message.MessageContent.Input args = messageContent.getInput();
                            if (messageContent.getName().equals("apply_test_class")) {
                                indicator.checkCanceled();
                                state.get(i).setCurrentState(TransitionStateClass.APPLY_DONE);
                                state.get(i).setNewTestSource(handleApplyTestClass(project, myConsole, args, generateTestsGetFilesCache, cutFileInfos.get(i).qualifiedName(), state.get(i).getNewTestSource(), messagesContentsRequestDTO, testFileInfos.get(i)));
                            }
                            if (messageContent.getName().equals("get_file")) handleGetFile(project, myConsole, args, messageContent.getId(), testFileInfos.get(i).filePath(), cutFileInfos.get(i).filePath(), classPathFetchedFlags.get(i), messagesContentsRequestDTO, generateTestsGetFilesCache, cutFileInfos.get(i).qualifiedName(), messageContent.getName());
                            if (messageContent.getName().equals("terminate_call")) {
                                allState = TERMINATED;
                                ConsolePrinter.warn(myConsole, "Maximum attempts reached. JAIPilot has made multiple attempts to generate, compile, and execute the tests but could not make further progress. Please review and fix any remaining issues manually. ");
                                Telemetry.allGenError(String.valueOf(attempt), "terminate call");
                            }
                        }
                        messagesRequestDTOs.get(i)
                                .addMessage(getMessageTool(MODEL_ROLE, messagesContentsRequestDTO.getMessageContentsModel())).addMessage(getMessageTool(USER_ROLE, messagesContentsRequestDTO.getMessageContentsUser()))
                                .addActualMessage(getMessageTool(MODEL_ROLE, messagesContentsRequestDTO.getActualMessageContentsModel())).addActualMessage(getMessageTool(USER_ROLE, messagesContentsRequestDTO.getActualMessageContentsUser()));
                    }
                    if(allState.equals(TERMINATED)) break;
                    // build all classes
                    ConsolePrinter.info(myConsole, "Compiling Tests ");
                    failedClassNamesBuilder = new StringBuilder();
                    indicator.checkCanceled();
                    allState = ALL_BUILD_SUCCESS;
                    for (int i = 0; i < testFileInfos.size(); i++) { // let's selectively rebuild all applied classes
                        if(state.get(i).shouldRebuild()) {
                            PsiJavaFile testFile = testFileInfos.get(i).psiFile();
                            String errorOutput = BuilderUtil.compileJUnitClass(project, testFile);
                            MessagesRequestDTO messagesRequestDTO = messagesRequestDTOs.get(i);
                            if (!errorOutput.isEmpty()) {
                                ConsolePrinter.info(myConsole, "Found compilation errors " + testFileInfos.get(i).simpleName());
                                failedClassNamesBuilder.append(testFile.getName());
                                messagesRequestDTO.addActualMessage(getMessage(USER_ROLE, errorOutput));
                                allState = ALL_BUILD_FAILURE;
                                state.get(i).setCurrentState(TransitionStateClass.BUILD_FAILURE);
                            } else state.get(i).setCurrentState(TransitionStateClass.BUILD_SUCCESS);
                        }
                        else if(state.get(i).isBuildFailure()) allState = ALL_BUILD_FAILURE;
                    }
                    // execute all classes
                    if (allState.equals(ALL_BUILD_SUCCESS)) {
                        ConsolePrinter.success(myConsole, "Compilation Successful. Running Tests");
                        indicator.checkCanceled();
                        allState = ALL_EXECUTION_SUCCESS;
                        for (int i = 0; i < testFileInfos.size(); i++) {
                            if(state.get(i).shouldExecute()) {
                                PsiJavaFile testFile = testFileInfos.get(i).psiFile();
                                String errorOutput = BuilderUtil.runJUnitClass(project, testFile);
                                MessagesRequestDTO messagesRequestDTO = messagesRequestDTOs.get(i);
                                if (!errorOutput.isEmpty()) {
                                    ConsolePrinter.info(myConsole, "Found tests execution errors " + testFile.getName());
                                    failedClassNamesBuilder.append(testFile.getName());
                                    messagesRequestDTO.addActualMessage(getMessage(USER_ROLE, errorOutput));
                                    allState = ALL_EXECUTION_FAILURE;
                                    state.get(i).setCurrentState(TransitionStateClass.EXECUTION_FAILURE);
                                } else state.get(i).setCurrentState(TransitionStateClass.EXECUTION_SUCCESS);
                            }
                            else if(state.get(i).isExecutionFailure()) allState = ALL_EXECUTION_FAILURE;
                        }
                        if (allState.equals(ALL_EXECUTION_SUCCESS)) {
                            ConsolePrinter.success(myConsole, "Tests execution successful");
                            if (generationType.equals(GenerationType.fix)) break;
                            boolean areAllInFinalExecutionState = true;
                            for (int i = 0; i < testFileInfos.size(); i++) {
                                if (!state.get(i).getCurrentState().equals(TransitionStateClass.EXECUTION_SUCCESS)) areAllInFinalExecutionState = false;
                            }
                            if(areAllInFinalExecutionState) break;
                        } else ConsolePrinter.info(myConsole, "Found tests execution errors " + failedClassNamesBuilder);
                    } else ConsolePrinter.info(myConsole, "Found compilation errors " + failedClassNamesBuilder);
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
