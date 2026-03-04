package com.github.skrcode.javaautounittests.util.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.state.AISettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class JAIPilotAuthService {
    private static final String WEBSITE_BASE = "https://www.jaipilot.com";
    private static final String LOGIN_URL = WEBSITE_BASE + "/plugin-login";
    private static final String REFRESH_URL = WEBSITE_BASE + "/plugin-refresh";
    private static final String NOTIFICATION_GROUP = "JAIPilot - One-Click AI Agent for Java Unit Testing Feedback";
    private static final String LOGIN_SUCCESS_TEMPLATE_RESOURCE = "/templates/jaipilot-login-success.html";
    private static final String LOGIN_SUCCESS_EMAIL_PLACEHOLDER = "{{SIGNED_IN_EMAIL}}";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long REFRESH_SKEW_SECONDS = 60L;
    private static final int LOGIN_TIMEOUT_SECONDS = 180;

    private JAIPilotAuthService() {}

    public static void startLogin(Project project, Runnable onUpdate) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                int port = server.getAddress().getPort();
                String redirectUri = "http://127.0.0.1:" + port + "/auth/callback";
                String state = UUID.randomUUID().toString();
                AtomicBoolean completed = new AtomicBoolean(false);
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "jaipilot-auth-timeout");
                    t.setDaemon(true);
                    return t;
                });

                server.createContext("/auth/callback", exchange -> {
                    try {
                        if (completed.get()) {
                            exchange.sendResponseHeaders(400, -1);
                            return;
                        }
                        Map<String, String> params = splitQuery(exchange.getRequestURI());
                        if (!state.equals(params.get("state"))) {
                            exchange.sendResponseHeaders(400, -1);
                            return;
                        }
                        String access = StringUtil.notNullize(params.get("access_token"));
                        String refresh = StringUtil.notNullize(params.get("refresh_token"));
                        long expiresAt = parseLongSafe(params.get("expires_at"), 0L);
                        String email = StringUtil.notNullize(params.get("email"));
                        if (access.isEmpty() || refresh.isEmpty() || email.isEmpty()) {
                            exchange.sendResponseHeaders(400, -1);
                            return;
                        }
                        persistSession(access, refresh, email, expiresAt);
                        completed.set(true);

                        String html = renderLoginSuccessHtml(email);
                        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "text/html");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                        notify(project, "Signed in", "JAIPilot account connected.", NotificationType.INFORMATION);
                        if (onUpdate != null) ApplicationManager.getApplication().invokeLater(onUpdate);
                    } finally {
                        scheduler.shutdownNow();
                        exchange.close();
                        server.stop(1);
                    }
                });
                server.start();
                scheduler.schedule(() -> {
                    if (completed.compareAndSet(false, true)) {
                        server.stop(0);
                        notify(project, "Sign-in timed out", "Login window expired. Please click Sign in again.", NotificationType.WARNING);
                        if (onUpdate != null) ApplicationManager.getApplication().invokeLater(onUpdate);
                    }
                }, LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                String authUrl = LOGIN_URL
                        + "?redirect_uri=" + urlEncode(redirectUri)
                        + "&state=" + urlEncode(state);
                BrowserUtil.browse(URI.create(authUrl));
            } catch (Exception ex) {
                notify(project, "Sign-in failed", "Could not open JAIPilot login: " + ex.getMessage(), NotificationType.ERROR);
            }
        });
    }

    public static void signOut() {
        AuthSessionStore.clear();
        AISettings settings = AISettings.getInstance();
        settings.setAuthEmail("");
        settings.setAccessTokenExpiresAtEpochSeconds(0L);
    }

    public static boolean hasConfiguredCredentials() {
        return !AuthSessionStore.getAccessToken().isBlank()
                || !AuthSessionStore.getRefreshToken().isBlank();
    }

    public static String getAuthEmail() {
        return AISettings.getInstance().getAuthEmail();
    }

    public static synchronized String getBearerToken() {
        String accessToken = AuthSessionStore.getAccessToken();
        if (accessToken.isBlank()) return refreshAccessToken();
        long now = Instant.now().getEpochSecond();
        long expiresAt = AISettings.getInstance().getAccessTokenExpiresAtEpochSeconds();
        if (expiresAt > 0 && expiresAt - REFRESH_SKEW_SECONDS > now) {
            return accessToken.trim();
        }
        String refreshed = refreshAccessToken();
        return refreshed.trim();
    }

    private static synchronized String refreshAccessToken() {
        String refreshToken = AuthSessionStore.getRefreshToken();
        if (refreshToken.isBlank()) return "";
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            String body = "{\"refresh_token\":\"" + escapeJson(refreshToken) + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(REFRESH_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                if (resp.statusCode() == 401 || resp.statusCode() == 403) signOut();
                return "";
            }

            JsonNode json = MAPPER.readTree(resp.body());
            String access = StringUtil.notNullize(optText(json, "access_token"));
            String refresh = StringUtil.notNullize(optText(json, "refresh_token"));
            String email = StringUtil.notNullize(optText(json, "email"));
            long expiresAt = json.has("expires_at") ? json.get("expires_at").asLong(0) : 0;
            if (access.isBlank()) return "";
            persistSession(access, refresh.isBlank() ? refreshToken : refresh, email, expiresAt);
            return access;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void persistSession(String accessToken, String refreshToken, String email, long expiresAt) {
        AuthSessionStore.saveTokens(accessToken, refreshToken);
        AISettings settings = AISettings.getInstance();
        settings.setAuthEmail(email);
        settings.setAccessTokenExpiresAtEpochSeconds(expiresAt);
    }

    private static void notify(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(title, content, type)
                .notify(project);
    }

    private static String optText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText("");
    }

    private static Map<String, String> splitQuery(URI uri) {
        Map<String, String> out = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) return out;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            out.put(urlDecode(pair.substring(0, idx)), urlDecode(pair.substring(idx + 1)));
        }
        return out;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static long parseLongSafe(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String renderLoginSuccessHtml(String email) {
        String safeEmail = escapeHtml(email);
        String template = defaultLoginSuccessTemplate();
        try (InputStream in = JAIPilotAuthService.class.getResourceAsStream(LOGIN_SUCCESS_TEMPLATE_RESOURCE)) {
            if (in != null) {
                template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // Fall back to the built-in minimal page if the resource cannot be loaded.
        }
        return template.replace(LOGIN_SUCCESS_EMAIL_PLACEHOLDER, safeEmail);
    }

    private static String defaultLoginSuccessTemplate() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>JAIPilot Connected</title>
                </head>
                <body style="font-family: 'Segoe UI', sans-serif; padding: 24px;">
                  <h1>JAIPilot is connected to IntelliJ</h1>
                  <p>Signed in as: {{SIGNED_IN_EMAIL}}</p>
                  <p>You can now safely close this tab and switch back to IntelliJ</p>
                </body>
                </html>
                """;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isBlank()) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
