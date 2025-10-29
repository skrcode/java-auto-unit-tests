// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AISettings", storages = @Storage("AISettings.xml"))
public class AISettings implements PersistentStateComponent<AISettings.State> {

    public static class State {
        public String proKey = "";
        public boolean telemetryEnabled = true;
    }

    private State state = new State();

    public static AISettings getInstance() {
        return com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(AISettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getProKey() {
        return state.proKey;
    }

    public void setProKey(String key) {
        state.proKey = key;
    }

    public boolean isTelemetryEnabled() { return state.telemetryEnabled; }
    public void setTelemetryEnabled(boolean v) { state.telemetryEnabled = v; }

}
