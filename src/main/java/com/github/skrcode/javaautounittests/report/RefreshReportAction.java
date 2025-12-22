package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public final class RefreshReportAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project p = e.getProject();
        if (p == null) return;
        JaipilotReportService.getInstance(p).refreshAsync("manual_refresh");
    }
}
