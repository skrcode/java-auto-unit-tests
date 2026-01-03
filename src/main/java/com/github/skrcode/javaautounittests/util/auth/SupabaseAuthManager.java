//// Copyright © 2025 Suraj Rajan / JAIPilot
//// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
//// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
//
//package com.github.skrcode.javaautounittests.util.auth;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.github.skrcode.javaautounittests.state.AISettings;
//import com.intellij.ide.BrowserUtil;
//import com.intellij.notification.NotificationGroupManager;
//import com.intellij.notification.NotificationType;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.project.Project;
//import org.jetbrains.annotations.Nullable;
//
//import java.io.OutputStream;
//import java.net.InetSocketAddress;
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.nio.charset.StandardCharsets;
//import java.time.Instant;
//import java.util.Map;
//import java.util.UUID;
//import java.util.concurrent.atomic.AtomicBoolean;
//
///**
// * Handles Supabase OAuth for the plugin using a loopback redirect to localhost.
// *
// * Flow:
// * - Start a short-lived HTTP listener on 127.0.0.1:{random}/auth/callback
// * - Open external browser to JAIPilot web login page with redirect back to that listener
// * - On callback, persist access/refresh tokens; refresh automatically before expiration
// */
//public final class SupabaseAuthManager {
//    private static final Logger LOG = Logger.getInstance(SupabaseAuthManager.class);
//    private static final ObjectMapper MAPPER = new ObjectMapper();
//
//    // TODO: replace with your public Supabase anon key
//    private static final String SUPABASE_URL = "https://otxfylhjrlaesjagfhfi.supabase.co";
//    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im90eGZ5bGhqcmxhZXNqYWdmaGZpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI5MDI3ODEsImV4cCI6MjA2ODQ3ODc4MX0.Dk1oQwpwerf1Xw0ejb00a6Su-jhyZ4hOWwQyiCMzHU8";
//    private static final long REFRESH_SKEW_SECONDS = 60; // refresh 1 min before expiry
//
//    private SupabaseAuthManager() {}
//
//    public static void signIn(Project project) {
//        try {
//            var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
//            int port = server.getAddress().getPort();
//            String redirectUri = "http://127.0.0.1:" + port + "/auth/callback";
//            String state = UUID.randomUUID().toString();
//
//            AtomicBoolean completed = new AtomicBoolean(false);
//            server.createContext("/auth/callback", exchange -> {
//                if (completed.get()) {
//                    exchange.sendResponseHeaders(400, -1);
//                    return;
//                }
//                try {
//                    Map<String, String> params = Urls.splitQuery(exchange.getRequestURI());
//                    if (!state.equals(params.get("state"))) {
//                        exchange.sendResponseHeaders(400, -1);
//                        return;
//                    }
//                    String accessToken = params.get("access_token");
//                    String refreshToken = params.get("refresh_token");
//                    long expiresIn = parseLongSafe(params.get("expires_in"), 0);
//                    if (accessToken == null || refreshToken == null || expiresIn <= 0) {
//                        exchange.sendResponseHeaders(400, -1);
//                        return;
//                    }
//                    long expiresAt = Instant.now().getEpochSecond() + expiresIn;
//                    AISettings settings = AISettings.getInstance();
//                    settings.setAccessToken(accessToken, expiresAt);
//                    settings.setRefreshToken(refreshToken);
//                    completed.set(true);
//
//                    String body = "<html><body><h3>JAIPilot</h3><p>You are now signed in. You can close this tab.</p></body></html>";
//                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
//                    exchange.getResponseHeaders().set("Content-Type", "text/html");
//                    exchange.sendResponseHeaders(200, bytes.length);
//                    try (OutputStream os = exchange.getResponseBody()) {
//                        os.write(bytes);
//                    }
//
//                    NotificationGroupManager.getInstance()
//                            .getNotificationGroup("JAIPilot - One-Click AI Agent for Java Unit Testing Feedback")
//                            .createNotification("Signed in", "JAIPilot is now linked to your account.", NotificationType.INFORMATION)
//                            .notify(project);
//                } catch (Exception ex) {
//                    LOG.warn("Supabase auth callback failed", ex);
//                    exchange.sendResponseHeaders(500, -1);
//                } finally {
//                    exchange.close();
//                    server.stop(1);
//                }
//            });
//            server.start();
//
//            String loginUrl = "https://www.jaipilot.com/plugin-login"
//                    + "?redirect_uri=" + Urls.urlEncode(redirectUri)
//                    + "&state=" + Urls.urlEncode(state);
//            BrowserUtil.browse(URI.create(loginUrl));
//        } catch (Exception ex) {
//            LOG.warn("Unable to start Supabase sign-in flow", ex);
//            NotificationGroupManager.getInstance()
//                    .getNotificationGroup("JAIPilot - One-Click AI Agent for Java Unit Testing Feedback")
//                    .createNotification("Sign-in failed", "Could not start JAIPilot login: " + ex.getMessage(), NotificationType.ERROR)
//                    .notify(project);
//        }
//    }
//
//    /**
//     * Ensure we have a valid access token; refresh using refresh_token if near expiry.
//     */
//    public static synchronized void ensureFreshAccessToken() {
//        AISettings settings = AISettings.getInstance();
//        long now = Instant.now().getEpochSecond();
//        long expiresAt = settings.getAccessTokenExpiresAtEpochSeconds();
//        boolean needsRefresh = expiresAt == 0 || expiresAt - REFRESH_SKEW_SECONDS < now;
//        if (!needsRefresh) return;
//
//        String refreshToken = settings.getRefreshToken();
//        if (refreshToken.isEmpty()) {
//            return;
//        }
//        try {
//            HttpClient http = HttpClient.newHttpClient();
//            String body = "grant_type=refresh_token&refresh_token=" + Urls.urlEncode(refreshToken);
//            HttpRequest req = HttpRequest.newBuilder()
//                    .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token"))
//                    .header("Content-Type", "application/x-www-form-urlencoded")
//                    .header("apikey", SUPABASE_ANON_KEY)
//                    .POST(HttpRequest.BodyPublishers.ofString(body))
//                    .build();
//            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
//            if (resp.statusCode() / 100 != 2) {
//                LOG.warn("Supabase refresh failed: " + resp.statusCode() + " " + resp.body());
//                return;
//            }
//            JsonNode json = MAPPER.readTree(resp.body());
//            String newAccess = optText(json, "access_token");
//            String newRefresh = optText(json, "refresh_token");
//            long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;
//            long newExpiresAt = Instant.now().getEpochSecond() + expiresIn;
//            if (newAccess != null && !newAccess.isEmpty()) {
//                settings.setAccessToken(newAccess, newExpiresAt);
//            }
//            if (newRefresh != null && !newRefresh.isEmpty()) {
//                settings.setRefreshToken(newRefresh);
//            }
//        } catch (Exception ex) {
//            LOG.warn("Supabase token refresh failed", ex);
//        }
//    }
//
//    @Nullable
//    private static String optText(JsonNode node, String field) {
//        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
//        return node.get(field).asText();
//    }
//
//    private static long parseLongSafe(String v, long defaultVal) {
//        try { return Long.parseLong(v); } catch (Exception ignored) { return defaultVal; }
//    }
//}
