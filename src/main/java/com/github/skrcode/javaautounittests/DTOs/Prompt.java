package com.github.skrcode.javaautounittests.DTOs;

public class Prompt {
    private String inputPlaceholder;
    private String errorOutputPlaceholder;
    private String systemInstructionsPlaceholder;

    public String getExistingTestClassPlaceholder() {
        return existingTestClassPlaceholder;
    }

    public void setExistingTestClassPlaceholder(String existingTestClassPlaceholder) {
        this.existingTestClassPlaceholder = existingTestClassPlaceholder;
    }

    private String existingTestClassPlaceholder;

    public String getSystemInstructionsPlaceholder() {
        return systemInstructionsPlaceholder;
    }

    public void setSystemInstructionsPlaceholder(String systemInstructionsPlaceholder) {
        this.systemInstructionsPlaceholder = systemInstructionsPlaceholder;
    }

    public String getInputPlaceholder() {
        return inputPlaceholder;
    }

    public void setInputPlaceholder(String inputPlaceholder) {
        this.inputPlaceholder = inputPlaceholder;
    }

    public String getErrorOutputPlaceholder() {
        return errorOutputPlaceholder;
    }

    public void setErrorOutputPlaceholder(String errorOutputPlaceholder) {
        this.errorOutputPlaceholder = errorOutputPlaceholder;
    }
}
