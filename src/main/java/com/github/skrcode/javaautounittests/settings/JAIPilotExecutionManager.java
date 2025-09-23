package com.github.skrcode.javaautounittests.settings;

public final class JAIPilotExecutionManager {
    private static volatile boolean cancelled = false;

    public static void cancel() {
        cancelled = true;
    }

    public static boolean isCancelled() {
        return cancelled;
    }

    public static void reset() {
        cancelled = false;
    }
}
