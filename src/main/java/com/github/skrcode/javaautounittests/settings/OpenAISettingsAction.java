package com.github.skrcode.javaautounittests.settings;

import com.github.skrcode.javaautounittests.settings.telemetry.Telemetry;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class OpenAISettingsAction extends AnAction {
    public OpenAISettingsAction() {
        super("Settings");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Telemetry.uiClick("settings");
        Project project = e.getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "JAIPilot - One-Click AI Agent for Java Unit Testing");
    }
}
