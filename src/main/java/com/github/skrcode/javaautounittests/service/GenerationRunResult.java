package com.github.skrcode.javaautounittests.service;

import org.jetbrains.annotations.NotNull;

public final class GenerationRunResult {

    public enum Status {
        SUCCESS,
        FAILED,
        CANCELLED
    }

    private final Status status;
    private final String summaryMessage;
    private final int attempts;
    private final long durationMs;

    public GenerationRunResult(
            @NotNull Status status,
            @NotNull String summaryMessage,
            int attempts,
            long durationMs
    ) {
        this.status = status;
        this.summaryMessage = summaryMessage;
        this.attempts = Math.max(0, attempts);
        this.durationMs = Math.max(0L, durationMs);
    }

    public static GenerationRunResult success(@NotNull String summaryMessage, int attempts, long durationMs) {
        return new GenerationRunResult(Status.SUCCESS, summaryMessage, attempts, durationMs);
    }

    public static GenerationRunResult failed(@NotNull String summaryMessage, int attempts, long durationMs) {
        return new GenerationRunResult(Status.FAILED, summaryMessage, attempts, durationMs);
    }

    public static GenerationRunResult cancelled(@NotNull String summaryMessage, int attempts, long durationMs) {
        return new GenerationRunResult(Status.CANCELLED, summaryMessage, attempts, durationMs);
    }

    public @NotNull Status status() {
        return status;
    }

    public @NotNull String summaryMessage() {
        return summaryMessage;
    }

    public int attempts() {
        return attempts;
    }

    public long durationMs() {
        return durationMs;
    }
}
