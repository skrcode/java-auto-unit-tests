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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spins up a background task that iterates over classes sequentially.
 */
public final class BulkGeneratorService {

    public static void enqueue(Project project, List<PsiClass> clazzes, GenerationType generationType) {
        String tabTitle = generationType.name();
        ApplicationManager.getApplication().invokeLater(() -> {
            AtomicReference<ProgressIndicator> indicatorRef = new AtomicReference<>();
            AtomicBoolean finished = new AtomicBoolean(false);
            AtomicBoolean cancelRequested = new AtomicBoolean(false);

            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
            if (toolWindow != null) toolWindow.show();
            ConsoleView console = ConsoleManager.openNewConsole(project, tabTitle, () -> {
                cancelRequested.set(true);
                ProgressIndicator indicator = indicatorRef.get();
                if (indicator != null && !indicator.isCanceled() && !finished.get()) {
                    indicator.cancel();
                }
            });
            ConsoleManager.print(console,
                    "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                            " 🚀 JAIPilot Test Generation\n" +
                            " Class: " + tabTitle + "\n" +
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n",
                    ConsoleViewContentType.SYSTEM_OUTPUT);

            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating tests for " + tabTitle, true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicatorRef.set(indicator);
                    if (cancelRequested.get()) {
                        indicator.cancel();
                        return;
                    }
                    if (indicator.isCanceled()) return;
                    ApplicationManager.getApplication().invokeLater(() -> ConsoleManager.print(console, "⚙️ Processing ", ConsoleViewContentType.NORMAL_OUTPUT));
                    TestGenerationWorker.process(project, clazzes, console, indicator, generationType);
                }
                @Override
                public void onCancel() {
                    finished.set(true);
                    ApplicationManager.getApplication().invokeLater(() -> ConsoleManager.print(console, "❌ JAIPilot generation cancelled by user.", ConsoleViewContentType.ERROR_OUTPUT));
                }
                @Override
                public void onSuccess() {
                    finished.set(true);
//                    ApplicationManager.getApplication().invokeLater(() -> {
//                        ConsoleManager.print(console,
//                                "✅ All tests generated successfully!",
//                                ConsoleViewContentType.SYSTEM_OUTPUT);
//
//
//                    });
                }
                @Override
                public void onThrowable(@NotNull Throwable error) {
                    finished.set(true);
                }
                @Override
                public void onFinished() {
                    indicatorRef.set(null);
                }
            });

        });
    }

}
