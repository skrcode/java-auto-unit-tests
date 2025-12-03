// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AIProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class AIProjectSettings implements PersistentStateComponent<AIProjectSettings.State> {

    public static final class State {
        public String testDirectory = "";
        public boolean autoCorrectEnabled = false;
        public boolean optimizeClassEnabled = false;
    }

    private State state = new State();

    public static AIProjectSettings getInstance(@NotNull Project project) {
        return project.getService(AIProjectSettings.class);
    }

    @Nullable @Override
    public State getState() { return state; }

    @Override
    public void loadState(@NotNull State state) { this.state = state; }

    public String getTestDirectory() { return state.testDirectory; }

    public void setTestDirectory(String dir) { state.testDirectory = dir; }


    public boolean getAutoCorrectEnabled() { return state.autoCorrectEnabled; }

    public void setAutoCorrectEnabled(boolean enabled) { state.autoCorrectEnabled = enabled; }

    public boolean getOptimizeClassEnabled() { return state.optimizeClassEnabled; }

    public void setOptimizeClassEnabled(boolean enabled) { state.optimizeClassEnabled = enabled; }
}
