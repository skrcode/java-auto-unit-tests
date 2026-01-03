/*
 * Copyright © 2026 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.util;

import com.github.skrcode.javaautounittests.dto.Message;
import org.apache.commons.collections.CollectionUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LLMMessageContentUtil {
    public static Message.MessageContent getMessageToolResultContent(String toolUseId, String content, boolean useCache) {
        // simple text input from user
        Map<String, String> cacheControl = new HashMap<>();
        cacheControl.put("type", "ephemeral");
        return Message.MessageContent.toolResult(toolUseId, content, useCache?cacheControl:null);
    }
    public static Message.MessageContent getMessageToolRequestContent(String toolUseId, String name, Message.MessageContent.Input input) {
        return Message.MessageContent.toolRequest(toolUseId, name, input);
    }
    public static Message.MessageContent getMessageTextContent(String textContent) {
        return Message.MessageContent.textContent(textContent);
    }
    public static Message getMessage(String role, String content, boolean useCache) {
        Map<String, String> cacheControl = new HashMap<>();
        cacheControl.put("type", "ephemeral");
        return new Message(role, Arrays.asList(Message.MessageContent.textContent(content == null ? "": content,useCache?cacheControl:null)));
    }
    public static Message getMessage(String role, String content) {
        return new Message(role, content == null ? "": content);
    }
    public static Message getMessageTool(String role, List<Message.MessageContent> messageContents) {
        if(CollectionUtils.isEmpty(messageContents)) return null;
        return new Message(role, messageContents);
    }
}
