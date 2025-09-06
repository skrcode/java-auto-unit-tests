package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.application.ApplicationManager;

public final class Telemetry {
    private Telemetry() {}
    private static TelemetryService svc() { return ApplicationManager.getApplication().getService(TelemetryService.class); }

    public static void uiClick(String trigger) {
        svc().log("ui_generate_clicked", trigger, null); // a=toolbar|context_menu
    }
    public static void allGenBegin(String className) {
        svc().log("generation_started", className, null);
    }
    public static void allGenDone(String className, String totalAttempts, long ms) {
        svc().log3("generation_completed", className, totalAttempts, "ms="+ms);
    }
    public static void allGenError(String totalAttempts, String reasonShort) {
        svc().log("generation_failed", totalAttempts, reasonShort);
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
}
