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
        private Input input; // optional structured input for tool_use
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

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Input {
            private String command;
            private String old_str;
            private String new_str;
            private String path;
            private String filePath;
            private String testPlan;
            private String file_text;

            public Input() {
            }

            public Input(String filePath) {
                this.filePath = filePath;
            }

            public Input(String command, String old_str, String new_str, String path) {
                this.command = command;
                this.old_str = old_str;
                this.new_str = new_str;
                this.path = path;
            }

            public Input(String command, String path, String file_text) {
                this.command = command;
                this.path = path;
                this.file_text = file_text;
            }

            public String getCommand() {
                return command;
            }

            public void setCommand(String command) {
                this.command = command;
            }

            public String getOld_str() {
                return old_str;
            }

            public void setOld_str(String old_str) {
                this.old_str = old_str;
            }

            public String getNew_str() {
                return new_str;
            }

            public void setNew_str(String new_str) {
                this.new_str = new_str;
            }

            public String getPath() {
                return path;
            }

            public void setPath(String path) {
                this.path = path;
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

            public String getFile_text() {
                return file_text;
            }

            public void setFile_text(String file_text) {
                this.file_text = file_text;
            }
        }
    }
}