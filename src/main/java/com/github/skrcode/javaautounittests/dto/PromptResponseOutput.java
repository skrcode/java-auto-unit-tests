// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.dto;

import java.util.List;

public class PromptResponseOutput {
    private List<Message> messages;
    private int errorCode;
    private String errorBody;

    public PromptResponseOutput() {
    }

    public PromptResponseOutput(List<Message> messages, int errorCode, String errorBody) {
        this.messages = messages;
        this.errorCode = errorCode;
        this.errorBody = errorBody;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorBody() {
        return errorBody;
    }

    public void setErrorBody(String errorBody) {
        this.errorBody = errorBody;
    }
}
