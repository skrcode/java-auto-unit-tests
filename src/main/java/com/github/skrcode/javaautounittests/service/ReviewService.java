/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.service;

import com.github.skrcode.javaautounittests.state.AISettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import javax.swing.event.HyperlinkEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.skrcode.javaautounittests.service.TelemetryService.getAppVersion;

public class ReviewService {
    public static void showReviewAfterTestGeneration(Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("JAIPilot - One-Click AI Agent for Java Unit Testing Feedback")
                .createNotification(
                        "All tests generated!",
                        """
                        If JAIPilot helped you, please <a href="review">leave a review</a> ⭐️<br><br>
                        Quick feedback: <a href="good">👍</a> &nbsp;&nbsp; <a href="bad">👎</a>
                        """,
                        NotificationType.INFORMATION
                )
                .setListener((notification, event) -> {
                    if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                        return;
                    }

                    String link = event.getDescription();

                    switch (link) {
                        case "review" -> {
                            BrowserUtil.browse("https://plugins.jetbrains.com/plugin/27706-jaipilot--ai-unit-test-generator/edit/reviews/new");
                            notification.expire();
                        }
                        case "good" -> {
                            sendFeedback(AISettings.getInstance().getProKey(), 5, getAppVersion());
                            showThanks(project);
                            notification.expire();
                        }
                        case "bad" -> {
                            sendFeedback(AISettings.getInstance().getProKey(), 1, getAppVersion());
                            showThanks(project);
                            notification.expire();
                        }
                    }
                })
                .notify(project);
    }

    private static void sendFeedback(String usageKey, int rating, String version) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String body = """
                {
                  "usage_key": "%s",
                  "rating": %d,
                  "app_version": "%s"
                }
                """.formatted(usageKey, rating, version);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://otxfylhjrlaesjagfhfi.supabase.co/functions/v1/give-feedback"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        });
    }

    private static void showThanks(Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("JAIPilot - One-Click AI Agent for Java Unit Testing Feedback")
                .createNotification("Thanks for your feedback!", NotificationType.INFORMATION)
                .notify(project);
    }
}
