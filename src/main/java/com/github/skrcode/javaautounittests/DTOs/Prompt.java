package com.github.skrcode.javaautounittests.DTOs;

import com.github.skrcode.javaautounittests.PromptBuilder;

public class Prompt {
    private String inputContextPlaceholder;
    private String errorOutputPlaceholder;
    private String systemInstructionsPlaceholder;
    private String systemInstructionsContextPlaceholder;
    private String generateMorePlaceholder;
    private String contextClassesSourcePlaceholder;

    public Prompt() {
        this.generateMorePlaceholder = PromptBuilder.getPromptPlaceholder("generate-more-prompt");
        this.systemInstructionsPlaceholder = PromptBuilder.getPromptPlaceholder("systeminstructions-prompt");
        this.systemInstructionsContextPlaceholder = PromptBuilder.getPromptPlaceholder("systeminstructions-context-prompt");
        this.errorOutputPlaceholder = PromptBuilder.getPromptPlaceholder("erroroutput-prompt");
        this.inputContextPlaceholder = PromptBuilder.getPromptPlaceholder("input-prompt");
        this.contextClassesSourcePlaceholder = PromptBuilder.getPromptPlaceholder("contextclasssource-prompt");
        this.existingTestClassPlaceholder = PromptBuilder.getPromptPlaceholder("testclass-prompt");
    }

    public String getInputContextPlaceholder() {
        return inputContextPlaceholder;
    }

    public void setInputContextPlaceholder(String inputContextPlaceholder) {
        this.inputContextPlaceholder = inputContextPlaceholder;
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

    public String getSystemInstructionsContextPlaceholder() {
        return systemInstructionsContextPlaceholder;
    }

    public void setSystemInstructionsContextPlaceholder(String systemInstructionsContextPlaceholder) {
        this.systemInstructionsContextPlaceholder = systemInstructionsContextPlaceholder;
    }

    public String getContextClassesSourcePlaceholder() {
        return contextClassesSourcePlaceholder;
    }

    public void setContextClassesSourcePlaceholder(String contextClassesSourcePlaceholder) {
        this.contextClassesSourcePlaceholder = contextClassesSourcePlaceholder;
    }
}
