/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.settings;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Feedback {

    private static final String FEEDBACK_URL =
            "https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/feedback";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /**
     * Print 👍 / 👎 at the end of generation.
     */
    public static void printFeedbackOptions(ConsoleView console, Project project, String cutName) {
        console.print("\n\nHow were these generated tests?  ", ConsoleViewContentType.NORMAL_OUTPUT);

        // 👍
        console.printHyperlink("👍", new HyperlinkInfo() {
            @Override
            public void navigate(Project project) {
                sendFeedbackAsync(cutName, "up");
            }
        });

        console.print("   ", ConsoleViewContentType.NORMAL_OUTPUT);

        // 👎
        console.printHyperlink("👎", new HyperlinkInfo() {
            @Override
            public void navigate(Project project) {
                sendFeedbackAsync(cutName, "down");
            }
        });

        console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }

    /**
     * Sends feedback asynchronously (fire-and-forget).
     * Uses retry logic similar to QuotaUtil.
     */
    public static void sendFeedbackAsync(String cutName, String rating) {
        new Thread(() -> {
            try {
                sendFeedback(cutName, rating);
            } catch (Exception ignored) {}
        }).start();
    }

    /**
     * Actual feedback sender with retry.
     */
    private static void sendFeedback(String cutName, String rating) throws Exception {
        String jsonBody = """
                {
                  "cut": "%s",
                  "rating": "%s"
                }
                """.formatted(cutName, rating);

        int retries = 0;
        final int MAX_RETRIES = 3;
        long backoffMillis = 1000;

        while (true) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(FEEDBACK_URL))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
                return; // success → exit

            } catch (Throwable t) {
                retries++;
                if (retries > MAX_RETRIES) return;

                long sleep = backoffMillis + (long) (Math.random() * 150);
                Thread.sleep(sleep);
                backoffMillis = Math.min(backoffMillis * 2, 10_000);
            }
        }
    }
}
