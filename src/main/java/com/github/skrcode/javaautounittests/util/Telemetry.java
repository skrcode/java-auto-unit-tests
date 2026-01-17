// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.util;

import com.github.skrcode.javaautounittests.dto.FileInfo;
import com.github.skrcode.javaautounittests.service.TelemetryService;
import com.intellij.openapi.application.ApplicationManager;

import java.util.List;
import java.util.stream.Collectors;

public final class Telemetry {
    private Telemetry() {}
    private static TelemetryService svc() { return ApplicationManager.getApplication().getService(TelemetryService.class); }

    public static void uiClick(String trigger) {
        svc().log("ui_generate_clicked", trigger, null); // a=toolbar|context_menu
    }
    public static void uiSettingsFailureClick(String trigger) {
        svc().log("ui_incorrect_settings_clicked", trigger, null); // a=toolbar|context_menu
    }
    public static void allGenBegin(String className) {
        svc().log("all_generation_started", className, null);
    }
    public static void allGenDone(String className, String totalAttempts, long ms) {
        svc().log3("all_generation_completed", className, totalAttempts, "ms="+ms);
    }
    public static void allGenError(String totalAttempts, String reasonShort) {
        svc().log("all_generation_failed", totalAttempts, reasonShort);
    }
    public static void genStarted(String className, String attempt) {
        svc().log("generation_started", className, attempt);
    }
    public static void genCompleted(String className, String attempt, long ms) {
        svc().log3("generation_completed", className, attempt, "ms="+ms);
    }
    public static void genFailed(String className, String attempt, String reasonShort) {
        svc().log3("generation_failed", className, attempt, reasonShort);
    }
    public static void coverage(String className, double line, double branch) {
        svc().log3("coverage_measured", className, "line="+line, "branch="+branch);
    }
    public static void bulkStart(int count) {
        svc().log("bulk_generation_started", "count="+count, null);
    }
    public static void bulkDone(int ok, long ms) {
        svc().log("bulk_generation_completed", "ok="+ok, "ms="+ms);
    }

    public static String getCombinedClassName(List<FileInfo> classes) {
        return classes.stream().map(FileInfo::simpleName).collect(Collectors.joining(","));
    }
}
