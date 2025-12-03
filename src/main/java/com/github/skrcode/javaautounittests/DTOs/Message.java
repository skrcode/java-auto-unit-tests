/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.DTOs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    private String role; // "user" or "assistant"
    private Object content; // may be String OR List<MessageContent>

    public Message() {}

    public Message(String role) {
        this.role = role;
    }

    public Message(String role, String textContent) {
        this.role = role;
        this.content = textContent;
    }

    public Message(String role, List<MessageContent> contentBlocks) {
        this.role = role;
        this.content = contentBlocks;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    // --- helper methods (must be ignored during JSON serialization) ---
    @JsonIgnore
    public String getContentAsString() {
        return content instanceof String ? (String) content : null;
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<MessageContent> getContentAsList() {
        return content instanceof List ? (List<MessageContent>) content : null;
    }

    @Override
    public String toString() {
        return "Message{" +
                "role='" + role + '\'' +
                ", content=" + content +
                '}';
    }

    // unified message block (covers tool_result, tool_use, and plain text)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageContent {
        private String type; // "text", "tool_use", or "tool_result"
        private String tool_use_id; // when type == "tool_result"
        private String content; // tool_result payload
        private String name; // optional, when type == "tool_use"
        private Object input; // optional structured input for tool_use
        private String id; // when type == "tool_use"
        Map<String, String> cache_control;

        public MessageContent() {}

        // Factory: tool_result
        public static MessageContent toolResult(String toolUseId, String content, Map<String, String> cacheControl) {
            MessageContent mc = new MessageContent();
            mc.type = "tool_result";
            mc.tool_use_id = toolUseId;
            mc.content = content;
            mc.cache_control = cacheControl;
            return mc;
        }

        public Map<String, String> getCache_control() {
            return cache_control;
        }

        public void setCache_control(Map<String, String> cache_control) {
            this.cache_control = cache_control;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTool_use_id() {
            return tool_use_id;
        }

        public void setTool_use_id(String tool_use_id) {
            this.tool_use_id = tool_use_id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object getInput() {
            return input;
        }

        public void setInput(Object input) {
            this.input = input;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}