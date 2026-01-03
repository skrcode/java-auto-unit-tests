/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.util;

import com.github.skrcode.javaautounittests.dto.CacheDTO;
import com.github.skrcode.javaautounittests.dto.Message;
import com.github.skrcode.javaautounittests.state.GenerateTestsGetFilesCache;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.github.skrcode.javaautounittests.service.GenerateTestsLLMService.USER_ROLE;
import static com.github.skrcode.javaautounittests.util.CUTUtil.stripCommentsAndMethodBodies;
import static com.github.skrcode.javaautounittests.util.LLMMessageContentUtil.getMessage;

public class GetFilesCacheUtil {

    public static List<String> getCachedGetFilesCachedPaths(GenerateTestsGetFilesCache generateTestsGetFilesCache, String cutFqn) {
        List<String> cachedPaths = Collections.emptyList();
        CacheDTO generateTestsGetFilesCacheEntry = generateTestsGetFilesCache.get(cutFqn);
        if (generateTestsGetFilesCacheEntry != null && generateTestsGetFilesCacheEntry.files != null) {
            cachedPaths = generateTestsGetFilesCacheEntry.files;
        }
        return cachedPaths;
    }

    public static void putCachedGetFilesCachedPaths(GenerateTestsGetFilesCache generateTestsGetFilesCache, String cutFqn, String filePath) {
        CacheDTO generateTestsGetFilesCacheEntry = generateTestsGetFilesCache.get(cutFqn);
        if (generateTestsGetFilesCacheEntry == null) generateTestsGetFilesCacheEntry = new CacheDTO(cutFqn);
        if (generateTestsGetFilesCacheEntry.files == null) generateTestsGetFilesCacheEntry.files = new ArrayList<>();
        if (!generateTestsGetFilesCacheEntry.files.contains(filePath)) generateTestsGetFilesCacheEntry.files.add(filePath);
        generateTestsGetFilesCache.put(cutFqn, generateTestsGetFilesCacheEntry);
    }

    public static void setCachedGetFilesCachedPaths(GenerateTestsGetFilesCache generateTestsGetFilesCache, String cutFqn, List<String> filePaths) {
        CacheDTO generateTestsGetFilesCacheEntry = generateTestsGetFilesCache.get(cutFqn);
        if (generateTestsGetFilesCacheEntry == null) generateTestsGetFilesCacheEntry = new CacheDTO(cutFqn);
        generateTestsGetFilesCacheEntry.files = new ArrayList<>(filePaths);
        generateTestsGetFilesCache.put(cutFqn, generateTestsGetFilesCacheEntry);
    }

    public static List<Message> getCachedGetFilesMessages(List<String> cachedPaths, ConsoleView myConsole, Project project, String testFilePath, String cutFilePath, Set<String> isClassPathFetched) {
        List<Message> messages = new ArrayList<>();
        for(String cachedFilePath: cachedPaths) {
            ConsolePrinter.info(myConsole, "Fetching file details from cache: " + cachedFilePath);
            String cachedFileContent = getFileContentFromCache(project, cachedFilePath, testFilePath, cutFilePath, isClassPathFetched);
            if (StringUtils.isEmpty(cachedFileContent)) {
                ConsolePrinter.info(myConsole, "Ignoring. Duplicate file or file not found: " + cachedFilePath);
                continue;
            }
            messages.add(getMessage(USER_ROLE,cachedFilePath +"=\n"+cachedFileContent));
            ConsolePrinter.success(myConsole, "Cached Snippet(s): " + cachedFileContent);
            ConsolePrinter.success(myConsole, "Fetched cached file snippet(s): " + cachedFileContent);
        }
        return messages;
    }

    public static String getFileContentFromCache(Project project, String cachedFilePath,  String testFilePath, String cutFilePath, Set<String> isClassPathFetched) {
        if(isClassPathFetched.contains(cachedFilePath) || cachedFilePath.contains(testFilePath) || cachedFilePath.contains(cutFilePath)) return null;
        isClassPathFetched.add(cachedFilePath);
        return stripCommentsAndMethodBodies(project, cachedFilePath);
    }
}
