package com.github.skrcode.javaautounittests.DTOs;

import java.util.List;

public class PromptResponseOutput {
    private String testClassCodeDiff;
    private List<String> contextClasses;

    public String getTestClassCodeDiff() {
        return testClassCodeDiff;
    }

    public void setTestClassCodeDiff(String testClassCodeDiff) {
        this.testClassCodeDiff = testClassCodeDiff;
    }

    public List<String> getContextClasses() {
        return contextClasses;
    }

    public void setContextClasses(List<String> contextClasses) {
        this.contextClasses = contextClasses;
    }
}
