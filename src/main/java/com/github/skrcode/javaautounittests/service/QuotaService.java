/*
 * Copyright © 2026 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.dto.QuotaResponse;
import com.github.skrcode.javaautounittests.util.ConsolePrinter;
import com.github.skrcode.javaautounittests.util.auth.JAIPilotAuthService;
import com.intellij.execution.ui.ConsoleView;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class QuotaService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static QuotaResponse fetchQuota()  {
        String url = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/fetch-quota";
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        int retries = 0;
        final int MAX_RETRIES = 5;
        long backoffMillis = 1500;
        while (true) {
            try {
                QuotaResponse out = new QuotaResponse();
                String bearerToken = JAIPilotAuthService.getBearerToken();
                if (bearerToken.isBlank()) {
                    out.error = "Session expired. Please sign in again in Settings.";
                    return out;
                }
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .header("Authorization", "Bearer " + bearerToken)
                        .GET()
                        .build();
                HttpResponse<String> resp =
                        http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int sc = resp.statusCode();
                if (sc / 100 == 4) {
                    // Surface client-side errors immediately and skip retries.
                    JsonNode errorBody = MAPPER.readTree(resp.body());
                    if (errorBody != null && errorBody.get("error") != null) {
                        out.error = errorBody.get("error").asText();
                    }
                    return out;
                }
                if (sc / 100 != 2) {
                    throw new RuntimeException("Unexpected quota fetch error");
                }
                // Success
                JsonNode json = MAPPER.readTree(resp.body());
                out.quotaUsed      = json.get("quotaUsed").asInt();
                out.quotaTotal     = json.get("quotaTotal").asInt();
                out.quotaRemaining = json.get("quotaRemaining").asInt();
                if(json.get("message") != null)
                    out.message = json.get("message").asText();
                return out;
            } catch (Throwable t) {
                retries++;
                if (retries > MAX_RETRIES) {
                    return new QuotaResponse();
                }
                long sleep = backoffMillis + (long) (Math.random() * 200); // jitter
                try {Thread.sleep(sleep);}
                catch (Exception e) {}
                backoffMillis = Math.min(backoffMillis * 2, 30_000);
            }
        }
    }

    public static void printQuotaWarning(ConsoleView myConsole) {
        QuotaResponse quotaResponse = QuotaService.fetchQuota();
        if(quotaResponse.message != null)
            ConsolePrinter.warn(myConsole, quotaResponse.message);
        if(quotaResponse.error != null) throw new RuntimeException(quotaResponse.error);
    }
}
