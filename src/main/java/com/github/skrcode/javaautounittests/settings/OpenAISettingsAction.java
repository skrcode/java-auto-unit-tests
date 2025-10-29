// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

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
        Telemetry.uiClick("settings right-click");
        Project project = e.getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "JAIPilot - One-Click AI Agent for Java Unit Testing");
    }
}
