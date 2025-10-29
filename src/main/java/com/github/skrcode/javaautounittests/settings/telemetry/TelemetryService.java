// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.settings.telemetry;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service(Service.Level.APP)
public final class TelemetryService {
    private static final Logger LOG = Logger.getInstance(TelemetryService.class);

    private final HttpClient http = HttpClient.newHttpClient();
    private final String sessionId = UUID.randomUUID().toString();

    // resolve once and cache
    private final String appVersion;

    public TelemetryService() {
        String ver = "unknown";
        try {
            var plugin = PluginManagerCore.getPlugin(
                    PluginId.getId("com.github.skrcode.javaautounittests") // must match <id> in plugin.xml
            );
            if (plugin != null) {
                ver = plugin.getVersion();
            }
        } catch (Exception e) {
            LOG.warn("Unable to resolve plugin version", e);
        }
        this.appVersion = ver;
    }

    public void log(String event, String a, String b) {
        send(event, a, b, null);
    }

    public void log3(String event, String a, String b, String c) {
        send(event, a, b, c);
    }

    private void send(String event, String a, String b, String c) {
        TelemetrySettings s = TelemetrySettings.getInstance();
        if (!s.enabled) return;
        try {
            String url = s.endpoint
                    + "?e=" + enc(event)
                    + "&s=" + enc(sessionId)
                    + "&v=" + enc(appVersion)   // include app_version
                    + (a != null ? "&a=" + enc(a) : "")
                    + (b != null ? "&b=" + enc(b) : "")
                    + (c != null ? "&c=" + enc(c) : "")
                    + (s.sharedKey != null && !s.sharedKey.isEmpty() ? "&k=" + enc(s.sharedKey) : "");

            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            http.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> { LOG.debug("telemetry beacon failed: " + ex.getMessage()); return null; });
        } catch (Exception ex) {
            LOG.debug("telemetry log error", ex);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
