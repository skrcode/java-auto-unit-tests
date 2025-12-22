package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class JaipilotReportToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JaipilotReportPanel panel = new JaipilotReportPanel(project);

        ContentFactory cf = ContentFactory.getInstance();
        Content content = cf.createContent(panel.getComponent(), "Report", false);
        toolWindow.getContentManager().addContent(content);

        // Initial build
        JaipilotReportService.getInstance(project).refreshAsync("toolwindow_open");
    }
}
