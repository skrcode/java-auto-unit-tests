package com.github.skrcode.javaautounittests.DTOs;

import java.util.List;

public class PromptInitialResponseOutput {
    private String testClassCode;
    private List<String> contextClasses;

    public String getTestClassCode() {
        return testClassCode;
    }

    public void setTestClassCode(String testClassCode) {
        this.testClassCode = testClassCode;
    }

    public List<String> getContextClasses() {
        return contextClasses;
    }

    public void setContextClasses(List<String> contextClasses) {
        this.contextClasses = contextClasses;
    }
}
