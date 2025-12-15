// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.DTOs;

public class PromptResponseOutput {
    private Message message;
    private int errorCode;
    private String errorBody;

    public PromptResponseOutput() {
    }

    public PromptResponseOutput(Message message, int errorCode, String errorBody) {
        this.message = message;
        this.errorCode = errorCode;
        this.errorBody = errorBody;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
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
