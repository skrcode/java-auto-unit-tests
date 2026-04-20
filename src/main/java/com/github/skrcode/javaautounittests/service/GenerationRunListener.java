package com.github.skrcode.javaautounittests.service;

import org.jetbrains.annotations.NotNull;

public interface GenerationRunListener {

    default void onQueued() {}

    default void onStarted() {}

    default void onStage(@NotNull String stage, @NotNull String message) {}

    default void onProgress(int percent) {}

    default void onDetail(@NotNull String detail) {}

    default void onFinished(@NotNull GenerationRunResult result) {}
}
