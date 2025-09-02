package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

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

    public void log(String event, String a, String b) {
        TelemetrySettings s = TelemetrySettings.getInstance();
        if (!s.enabled) return;
        try {
            String url = s.endpoint
                    + "?e=" + enc(event)
                    + "&s=" + enc(sessionId)
                    + (a != null ? "&a=" + enc(a) : "")
                    + (b != null ? "&b=" + enc(b) : "")
                    + (s.sharedKey != null && !s.sharedKey.isEmpty() ? "&k=" + enc(s.sharedKey) : "");

            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            // fire-and-forget (don’t block EDT)
            http.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> { LOG.debug("telemetry beacon failed: " + ex.getMessage()); return null; });
        } catch (Exception ex) {
            LOG.debug("telemetry log error", ex);
        }
    }

    public void log3(String event, String a, String b, String c) {
        TelemetrySettings s = TelemetrySettings.getInstance();
        if (!s.enabled) return;
        try {
            String url = s.endpoint
                    + "?e=" + enc(event)
                    + "&s=" + enc(sessionId)
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

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
