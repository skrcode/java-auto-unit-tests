/*
 * Copyright © 2026 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.util;

import com.github.skrcode.javaautounittests.dto.FileInfo;
import com.github.skrcode.javaautounittests.dto.Message;
import com.github.skrcode.javaautounittests.dto.MessagesContentsRequestDTO;
import com.github.skrcode.javaautounittests.state.GenerateTestsGetFilesCache;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.github.skrcode.javaautounittests.util.GetFilesCacheUtil.*;
import static com.github.skrcode.javaautounittests.util.LLMMessageContentUtil.*;

public class ToolHandlerUtil {

    public static String handleApplyTestClass(Project project, @NotNull ConsoleView myConsole, Message.MessageContent.Input args, GenerateTestsGetFilesCache generateTestsGetFilesCache, String cutFqn, String oldTestSource, MessagesContentsRequestDTO messagesContentsRequestDTO, FileInfo testFileInfo) {
        try {
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
            String newTestSource = BuilderUtil.buildAndWriteTestClass(project, testFileInfo.psiFile(), classSkeleton, methods, myConsole);
            messagesContentsRequestDTO.addActualMessageContentModel(getMessageTextContent(testFileInfo.simpleName() + " = \n" + (newTestSource != null?newTestSource.stripTrailing():oldTestSource)));
            if (CollectionUtils.isNotEmpty(args.getFilesUsed())) setCachedGetFilesCachedPaths(generateTestsGetFilesCache, cutFqn, args.getFilesUsed());
            return (newTestSource != null ? newTestSource : oldTestSource);
        }
        catch (Exception e) {
            ConsolePrinter.info(myConsole, "⚠️ Error composing test class: " + e.getMessage());
            return oldTestSource;
        }
    }

    public static void handleGetFile(Project project, @NotNull ConsoleView myConsole, Message.MessageContent.Input args, String toolUseId, String testFilePath, String cutFilePath, Set<String> isClassPathFetched, MessagesContentsRequestDTO messagesContentsRequestDTO, GenerateTestsGetFilesCache generateTestsGetFilesCache, String cutFqn, String fn) {
        String filePath = args.getFilePath();
        ConsolePrinter.info(myConsole, "Fetching file details: " + filePath);
        String toolResult = getFileContentFromCache(project, filePath,testFilePath, cutFilePath ,isClassPathFetched);
        if (StringUtils.isEmpty(toolResult)) {
            ConsolePrinter.info(myConsole, "Duplicate File, No matches or file not found: " + filePath);
            toolResult = filePath + " duplicate, not found or no matches found.";
        } else {
            ConsolePrinter.success(myConsole, "Snippet(s): " + toolResult);
            ConsolePrinter.success(myConsole, "Fetched file snippet(s): " + filePath);
        }
        messagesContentsRequestDTO.addToBothUser(getMessageToolResultContent(toolUseId, toolResult, false));
        messagesContentsRequestDTO.addToBothModel(getMessageToolRequestContent(toolUseId, fn, args));
        putCachedGetFilesCachedPaths(generateTestsGetFilesCache, cutFqn, filePath);
    }
}
