/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.settings.actions;

import com.github.skrcode.javaautounittests.settings.AIProjectSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class OptimizeClassAction extends ToggleAction {

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return false;

        AIProjectSettings settings = AIProjectSettings.getInstance(project);
        return settings.getOptimizeClassEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        Project project = e.getProject();
        if (project == null) return;

        AIProjectSettings settings = AIProjectSettings.getInstance(project);
        settings.setOptimizeClassEnabled(state);
    }
    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
    }
}