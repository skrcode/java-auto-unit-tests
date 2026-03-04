package com.github.skrcode.javaautounittests.view.report;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record InlineFixRowState(
        @NotNull String runId,
        @NotNull String cutFqn,
        @NotNull Status status,
        @NotNull String stage,
        @NotNull String latestMessage,
        int progressPercent,
        long elapsedMs,
        boolean canCancel,
        boolean expanded,
        @NotNull List<String> detailLines
) {
    public enum Status {
        IDLE,
        QUEUED,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    public static final int MAX_DETAIL_LINES = 8;

    public InlineFixRowState {
        progressPercent = Math.max(0, Math.min(100, progressPercent));
        elapsedMs = Math.max(0L, elapsedMs);
        detailLines = detailLines == null ? List.of() : List.copyOf(detailLines);
    }

    public static @NotNull InlineFixRowState queued(@NotNull String cutFqn) {
        return new InlineFixRowState(
                "",
                cutFqn,
                Status.QUEUED,
                "Queued",
                "Waiting for previous class run to finish.",
                0,
                0L,
                true,
                false,
                List.of("Queued for inline fix run.")
        );
    }

    public static @NotNull InlineFixRowState running(@NotNull String cutFqn, @NotNull String runId) {
        return new InlineFixRowState(
                runId,
                cutFqn,
                Status.RUNNING,
                "Starting",
                "Initializing generation run.",
                0,
                0L,
                true,
                false,
                List.of()
        );
    }

    public @NotNull InlineFixRowState withRunId(@NotNull String newRunId) {
        return new InlineFixRowState(
                newRunId,
                cutFqn,
                status,
                stage,
                latestMessage,
                progressPercent,
                elapsedMs,
                canCancel,
                expanded,
                detailLines
        );
    }

    public @NotNull InlineFixRowState withStatus(
            @NotNull Status newStatus,
            @NotNull String newStage,
            @NotNull String newMessage,
            boolean allowCancel
    ) {
        return new InlineFixRowState(
                runId,
                cutFqn,
                newStatus,
                newStage,
                newMessage,
                progressPercent,
                elapsedMs,
                allowCancel,
                expanded,
                detailLines
        );
    }

    public @NotNull InlineFixRowState withProgress(int progress) {
        return new InlineFixRowState(
                runId,
                cutFqn,
                status,
                stage,
                latestMessage,
                progress,
                elapsedMs,
                canCancel,
                expanded,
                detailLines
        );
    }

    public @NotNull InlineFixRowState withElapsedMs(long millis) {
        return new InlineFixRowState(
                runId,
                cutFqn,
                status,
                stage,
                latestMessage,
                progressPercent,
                millis,
                canCancel,
                expanded,
                detailLines
        );
    }

    public @NotNull InlineFixRowState toggleExpanded() {
        if (detailLines.isEmpty()) return this;
        return new InlineFixRowState(
                runId,
                cutFqn,
                status,
                stage,
                latestMessage,
                progressPercent,
                elapsedMs,
                canCancel,
                !expanded,
                detailLines
        );
    }

    public @NotNull InlineFixRowState appendDetail(@NotNull String line) {
        if (line.isBlank()) return this;
        List<String> next = new ArrayList<>(detailLines);
        next.add(line);
        if (next.size() > MAX_DETAIL_LINES) {
            next = new ArrayList<>(next.subList(next.size() - MAX_DETAIL_LINES, next.size()));
        }
        return new InlineFixRowState(
                runId,
                cutFqn,
                status,
                stage,
                line,
                progressPercent,
                elapsedMs,
                canCancel,
                expanded,
                Collections.unmodifiableList(next)
        );
    }
}
