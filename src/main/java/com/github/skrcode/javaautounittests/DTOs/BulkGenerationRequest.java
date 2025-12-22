package com.github.skrcode.javaautounittests.DTOs;

import com.intellij.execution.ui.ConsoleView;

import java.util.List;

public final class BulkGenerationRequest {
    public final String testFileName;
    public final List<Content> contents;
    public final ConsoleView console;
    public final int attempt;

    public BulkGenerationRequest(String testFileName,
                                 List<Content> contents,
                                 ConsoleView console,
                                 int attempt) {
        this.testFileName = testFileName;
        this.contents = contents;
        this.console = console;
        this.attempt = attempt;
    }
}