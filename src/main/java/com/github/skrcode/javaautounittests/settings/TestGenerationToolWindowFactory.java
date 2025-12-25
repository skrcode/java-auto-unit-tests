// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.settings;

import com.github.skrcode.javaautounittests.report.ReportView;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * ToolWindowFactory creates the reporting panel as initial content.
 * Console tabs are created later by JaipilotConsoleManager.openNewConsole().
 */
public class TestGenerationToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ReportView panel = ReportView.getInstance(project);
        if (panel == null) {
            // Fallback in case service lookup failed
            panel = new ReportView(project);
        }

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel.getComponent(), "Test Health", false);
        toolWindow.getContentManager().addContent(content);

        // Initial build
        panel.refreshAsync("toolwindow_open");
    }
}
