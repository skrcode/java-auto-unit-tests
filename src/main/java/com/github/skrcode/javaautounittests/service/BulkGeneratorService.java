// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.service;

import com.github.skrcode.javaautounittests.constants.GenerationType;
import com.github.skrcode.javaautounittests.worker.TestGenerationWorker;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Spins up a background task that iterates over classes sequentially.
 */
public final class BulkGeneratorService {

    private BulkGeneratorService() {}

    public static void enqueue(Project project, List<PsiClass> clazzes, GenerationType generationType) {
        enqueue(project, clazzes, generationType, RunOutputMode.CONSOLE_TAB, null);
    }

    public static @NotNull GenerationJobHandle enqueue(
            @NotNull Project project,
            @NotNull List<PsiClass> clazzes,
            @NotNull GenerationType generationType,
            @NotNull RunOutputMode outputMode,
            @Nullable GenerationRunListener listener
    ) {
        String runId = UUID.randomUUID().toString();
        AtomicReference<ProgressIndicator> indicatorRef = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicBoolean cancelRequested = new AtomicBoolean(false);
        AtomicReference<GenerationRunResult> resultRef = new AtomicReference<>();

        GenerationJobHandle handle = new GenerationJobHandle() {
            @Override
            public String runId() {
                return runId;
            }

            @Override
            public void cancel() {
                cancelRequested.set(true);
                ProgressIndicator indicator = indicatorRef.get();
                if (indicator != null && !indicator.isCanceled()) {
                    indicator.cancel();
                }
            }

            @Override
            public boolean isFinished() {
                return finished.get();
            }
        };

        safeNotify(listener, GenerationRunListener::onQueued);

        Runnable launch = () -> {
            if (project.isDisposed()) {
                finished.set(true);
                GenerationRunResult skipped = GenerationRunResult.failed(
                        "Project disposed before generation could start.",
                        0,
                        0
                );
                resultRef.set(skipped);
                safeNotify(listener, l -> l.onFinished(skipped));
                return;
            }

            ConsoleView console = null;
            if (outputMode == RunOutputMode.CONSOLE_TAB) {
                String tabTitle = generationType.name();
                ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
                if (toolWindow != null) toolWindow.show();

                console = ConsoleManager.openNewConsole(project, tabTitle, handle::cancel);
                ConsoleManager.print(console,
                        "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                                " JAIPilot Test Generation\n" +
                                " Class: " + tabTitle + "\n" +
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n",
                        ConsoleViewContentType.SYSTEM_OUTPUT);
            }

            ConsoleView finalConsole = console;
            ProgressManager.getInstance().run(new Task.Backgroundable(
                    project,
                    "Generating tests for " + generationType.name(),
                    true
            ) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicatorRef.set(indicator);
                    if (cancelRequested.get()) {
                        indicator.cancel();
                        return;
                    }
                    safeNotify(listener, GenerationRunListener::onStarted);

                    if (outputMode == RunOutputMode.CONSOLE_TAB && !indicator.isCanceled()) {
                        ConsoleManager.print(finalConsole, "Processing", ConsoleViewContentType.NORMAL_OUTPUT);
                    }

                    GenerationRunResult result = TestGenerationWorker.process(
                            project,
                            clazzes,
                            finalConsole,
                            indicator,
                            generationType,
                            listener
                    );
                    resultRef.compareAndSet(null, result);
                }

                @Override
                public void onCancel() {
                    if (outputMode == RunOutputMode.CONSOLE_TAB) {
                        ConsoleManager.print(finalConsole,
                                "JAIPilot generation cancelled by user.",
                                ConsoleViewContentType.ERROR_OUTPUT
                        );
                    }
                    resultRef.compareAndSet(
                            null,
                            GenerationRunResult.cancelled("Generation cancelled.", 0, 0)
                    );
                }

                @Override
                public void onThrowable(@NotNull Throwable error) {
                    resultRef.compareAndSet(
                            null,
                            GenerationRunResult.failed(
                                    error.getMessage() == null ? "Generation failed." : error.getMessage(),
                                    0,
                                    0
                            )
                    );
                }

                @Override
                public void onFinished() {
                    indicatorRef.set(null);
                    finished.set(true);
                    GenerationRunResult out = resultRef.get();
                    if (out == null) {
                        out = cancelRequested.get()
                                ? GenerationRunResult.cancelled("Generation cancelled.", 0, 0)
                                : GenerationRunResult.failed("Generation did not complete.", 0, 0);
                    }
                    GenerationRunResult finalOut = out;
                    safeNotify(listener, l -> l.onFinished(finalOut));
                }
            });
        };

        if (outputMode == RunOutputMode.CONSOLE_TAB) {
            ApplicationManager.getApplication().invokeLater(launch);
        } else {
            launch.run();
        }

        return handle;
    }

    private static void safeNotify(@Nullable GenerationRunListener listener, @NotNull Consumer<GenerationRunListener> fn) {
        if (listener == null) return;
        try {
            fn.accept(listener);
        } catch (Throwable ignored) {
            // Listener errors should not affect generation flow.
        }
    }
}
