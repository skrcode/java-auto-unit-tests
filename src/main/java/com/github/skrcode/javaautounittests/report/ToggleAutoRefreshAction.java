package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class ToggleAutoRefreshAction extends ToggleAction {
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        Project p = e.getProject();
        return p != null && JaipilotReportService.getInstance(p).isAutoRefreshEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        Project p = e.getProject();
        if (p == null) return;
        JaipilotReportService.getInstance(p).setAutoRefreshEnabled(state);
    }
}
