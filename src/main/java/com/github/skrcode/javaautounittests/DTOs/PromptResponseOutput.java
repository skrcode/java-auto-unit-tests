package com.github.skrcode.javaautounittests.DTOs;

import java.util.Set;

public class PromptResponseOutput {
    private String testClassCode;
    private Set<String> contextClasses;

    public String getTestClassCode() {
        return testClassCode;
    }

    public void setTestClassCode(String testClassCode) {
        this.testClassCode = testClassCode;
    }

    public Set<String> getContextClasses() {
        return contextClasses;
    }

    public void setContextClasses(Set<String> contextClasses) {
        this.contextClasses = contextClasses;
    }
}
