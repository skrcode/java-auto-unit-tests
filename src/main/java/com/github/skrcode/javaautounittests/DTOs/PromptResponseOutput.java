package com.github.skrcode.javaautounittests.DTOs;

public class PromptResponseOutput {
    private Content content;
    private int errorCode;

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
}
