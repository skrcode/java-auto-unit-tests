// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.DTOs;

import java.util.List;

public class Content {
    private String role;             // "user" or "model"
    private List<Part> parts;        // list of message parts

    public Content() {}

    public Content(String role, List<Part> parts) {
        this.role = role;
        this.parts = parts;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<Part> getParts() {
        return parts;
    }

    public void setParts(List<Part> parts) {
        this.parts = parts;
    }

    @Override
    public String toString() {
        return "Content{" +
                "role='" + role + '\'' +
                ", parts=" + parts +
                '}';
    }

    // Nested DTO for each part of the message
    public static class Part {
        private String thoughtSignature;
        private String text;
        private FunctionCall functionCall;
        private FunctionResponse functionResponse;

        public Part() {}

        public Part(String text) {
            this.text = text;
        }

        public Part(FunctionCall functionCall) {
            this.functionCall = functionCall;
        }

        public Part(FunctionResponse functionResponse) {
            this.functionResponse = functionResponse;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public FunctionCall getFunctionCall() {
            return functionCall;
        }

        public void setFunctionCall(FunctionCall functionCall) {
            this.functionCall = functionCall;
        }

        public String getThoughtSignature() {
            return thoughtSignature;
        }

        public void setThoughtSignature(String thoughtSignature) {
            this.thoughtSignature = thoughtSignature;
        }

        public FunctionResponse getFunctionResponse() {
            return functionResponse;
        }

        public void setFunctionResponse(FunctionResponse functionResponse) {
            this.functionResponse = functionResponse;
        }

        @Override
        public String toString() {
            return "Part{" +
                    "thoughtSignature='" + thoughtSignature + '\'' +
                    ", text='" + text + '\'' +
                    ", functionCall=" + functionCall +
                    ", functionResponse=" + functionResponse +
                    '}';
        }
    }

    // DTO for tool / function call inside parts
    public static class FunctionCall {
        private String name;
        private Object args; // can be mapped to Map<String, Object> if you want structured args

        public FunctionCall() {}

        public FunctionCall(String name, Object args) {
            this.name = name;
            this.args = args;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object getArgs() {
            return args;
        }

        public void setArgs(Object args) {
            this.args = args;
        }

        @Override
        public String toString() {
            return "FunctionCall{" +
                    "name='" + name + '\'' +
                    ", args=" + args +
                    '}';
        }
    }

    public static class FunctionResponse {
        private String name;
        private FunctionResponseResult response;

        public FunctionResponse() {
        }

        public FunctionResponse(String name, FunctionResponseResult response) {
            this.name = name;
            this.response = response;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Object getResponse() {
            return response;
        }

        public void setResponse(FunctionResponseResult response) {
            this.response = response;
        }

        @Override
        public String toString() {
            return "FunctionResponse{" +
                    "name='" + name + '\'' +
                    ", response=" + response +
                    '}';
        }
    }

    public static class FunctionResponseResult {
        private String result;

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        @Override
        public String toString() {
            return "FunctionResponseResult{" +
                    "result='" + result + '\'' +
                    '}';
        }
    }

}
