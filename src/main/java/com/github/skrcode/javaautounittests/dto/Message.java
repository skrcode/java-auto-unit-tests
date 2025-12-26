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
package com.github.skrcode.javaautounittests.dto;

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
        private Input input; // optional structured input for tool_use
        private String id; // when type == "tool_use"
        Map<String, String> cache_control;
        private String signature; // thinking
        private String data; // thinking
        private String thinking;
        private String text;

        public MessageContent() {}

        public static MessageContent textContent(String textContent) {
            MessageContent mc = new MessageContent();
            mc.type = "text";
            mc.text = textContent;
            return mc;
        }

        public static MessageContent textContent(String textContent, Map<String, String> cacheControl) {
            MessageContent mc = new MessageContent();
            mc.type = "text";
            mc.text = textContent;
            mc.cache_control = cacheControl;
            return mc;
        }

        // Factory: tool_result
        public static MessageContent toolResult(String toolUseId, String content, Map<String, String> cacheControl) {
            MessageContent mc = new MessageContent();
            mc.type = "tool_result";
            mc.tool_use_id = toolUseId;
            mc.content = content;
            mc.cache_control = cacheControl;
            return mc;
        }

        public static MessageContent toolRequest(String toolUseId, String name, Input input) {
            MessageContent mc = new MessageContent();
            mc.type = "tool_use";
            mc.id = toolUseId;
            mc.name = name;
            mc.input = input;
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

        public Input getInput() {
            return input;
        }

        public void setInput(Input input) {
            this.input = input;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getThinking() {
            return thinking;
        }

        public void setThinking(String thinking) {
            this.thinking = thinking;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Input {
            private String command;
            private String filePath;
            private String testPlan;
            private String classSkeleton;
            private Object methods;
            private List<String> filesUsed;

            public Input(String classSkeleton, Object methods) {
                this.classSkeleton = classSkeleton;
                this.methods = methods;
            }

            public Input(Object methods) {
                this.methods = methods;
            }

            public Input() {
            }

            public Input(String filePath) {
                this.filePath = filePath;
            }

            public String getCommand() {
                return command;
            }

            public void setCommand(String command) {
                this.command = command;
            }

            public String getFilePath() {
                return filePath;
            }

            public void setFilePath(String filePath) {
                this.filePath = filePath;
            }

            public String getTestPlan() {
                return testPlan;
            }

            public void setTestPlan(String testPlan) {
                this.testPlan = testPlan;
            }

            public String getClassSkeleton() {
                return classSkeleton;
            }

            public void setClassSkeleton(String classSkeleton) {
                this.classSkeleton = classSkeleton;
            }

            public Object getMethods() {
                return methods;
            }

            public void setMethods(Object methods) {
                this.methods = methods;
            }

            public List<String> getFilesUsed() {
                return filesUsed;
            }

            public void setFilesUsed(List<String> filesUsed) {
                this.filesUsed = filesUsed;
            }
        }
    }
}