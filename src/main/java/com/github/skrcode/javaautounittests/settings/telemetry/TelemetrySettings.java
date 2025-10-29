// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.settings.telemetry;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;

@State(
        name = "JAIPilotTelemetrySettings",
        storages = @Storage("jaipilot_telemetry.xml")
)
@Service(Service.Level.APP)
public final class TelemetrySettings implements PersistentStateComponent<TelemetrySettings> {
    public boolean enabled = true; // default: ON (user can opt out)
    public String endpoint = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/t";
    public String sharedKey = "SkFJUGlsb3Q="; // or empty if not using

    public static TelemetrySettings getInstance() {
        return ApplicationManager.getApplication().getService(TelemetrySettings.class);
    }

    @Override
    public TelemetrySettings getState() {
        return this;
    }

    @Override
    public void loadState(TelemetrySettings state) {
        this.enabled = state.enabled;
        this.endpoint = state.endpoint;
        this.sharedKey = state.sharedKey;
    }
}
