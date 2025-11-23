/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.skrcode.javaautounittests.DTOs.QuotaResponse;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class QuotaUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static QuotaResponse fetchQuota(
    ) throws Exception {
        String baseUrl = "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/fetch-quota";
        String url = baseUrl + "?licenseKey=" + URLEncoder.encode(AISettings.getInstance().getState().proKey, StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        int retries = 0;
        final int MAX_RETRIES = 5;
        long backoffMillis = 1500;
        while (true) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> resp =
                        http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int sc = resp.statusCode();
                if (sc / 100 != 2) {
                    throw new RuntimeException("Unexpected quota fetch error: " + sc + " " + resp.body());
                }
                // Success
                JsonNode json = MAPPER.readTree(resp.body());

                QuotaResponse out = new QuotaResponse();
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
                Thread.sleep(sleep);
                backoffMillis = Math.min(backoffMillis * 2, 30_000);
            }
        }
    }
}
