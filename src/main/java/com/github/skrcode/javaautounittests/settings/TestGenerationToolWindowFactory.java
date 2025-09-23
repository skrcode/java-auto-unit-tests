package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

/**
 * ToolWindowFactory only needs to register the toolwindow itself.
 * Actual console tabs are created later by JaipilotConsoleManager.openNewConsole().
 */
public class TestGenerationToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {

    }
}
