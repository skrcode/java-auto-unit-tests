// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.actions;

import com.github.skrcode.javaautounittests.view.report.ReportView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * ToolWindowFactory creates the reporting panel as initial content.
 * Console tabs are created later by JaipilotConsoleManager.openNewConsole().
 */
public class TestGenerationToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        if (toolWindow.getContentManager().getContentCount() > 0) return;

        ReportView reportView = ReportView.getInstance(project);
        JComponent contentComponent = reportView != null ? reportView.getComponent() : buildFallbackPanel();

        Content content = ContentFactory.getInstance().createContent(contentComponent, "Overview", false);
        content.setCloseable(false);
        toolWindow.getContentManager().addContent(content);
        if (reportView != null) {
            ApplicationManager.getApplication().invokeLater(() -> reportView.refreshAsync("toolwindow_open"));
        }
    }

    private static JComponent buildFallbackPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setBackground(UIManager.getColor("Panel.background"));

        JLabel title = new JLabel("JAIPilot Console");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 1f));

        JLabel subtitle = new JLabel(
                "<html>Run <b>JAIPilot – Generate Tests and Improve Coverage</b> from the editor or project view.<br>"
                        + "Execution logs and progress will appear here.</html>"
        );
        subtitle.setForeground(new JBColor(new Color(95, 99, 104), new Color(151, 151, 156)));
        subtitle.setBorder(new EmptyBorder(8, 0, 0, 0));

        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.CENTER);
        return panel;
    }
}
