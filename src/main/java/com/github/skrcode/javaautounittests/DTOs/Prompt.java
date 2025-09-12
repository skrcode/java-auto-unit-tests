package com.github.skrcode.javaautounittests.DTOs;

public class Prompt {
    private String inputPlaceholder;
    private String inputContextPlaceholder;
    private String errorOutputPlaceholder;
    private String systemInstructionsPlaceholder;
    private String generateMorePlaceholder;
    private String systemInstructionsContextPlaceholder;


    public String getInputContextPlaceholder() {
        return inputContextPlaceholder;
    }

    public void setInputContextPlaceholder(String inputContextPlaceholder) {
        this.inputContextPlaceholder = inputContextPlaceholder;
    }

    public String getSystemInstructionsContextPlaceholder() {
        return systemInstructionsContextPlaceholder;
    }

    public void setSystemInstructionsContextPlaceholder(String systemInstructionsContextPlaceholder) {
        this.systemInstructionsContextPlaceholder = systemInstructionsContextPlaceholder;
    }

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
    public String getGenerateMorePlaceholder() {
        return generateMorePlaceholder;
    }

    public void setGenerateMorePlaceholder(String generateMorePlaceholder) {
        this.generateMorePlaceholder = generateMorePlaceholder;
    }
}
