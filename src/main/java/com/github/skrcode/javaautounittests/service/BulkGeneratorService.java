// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.service;

import com.github.skrcode.javaautounittests.GenerationType;
import com.github.skrcode.javaautounittests.worker.TestGenerationWorker;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Spins up a background task that iterates over classes sequentially.
 */
public final class BulkGeneratorService {

    public static void enqueue(Project project, List<PsiClass> clazzes, GenerationType generationType) {
        PsiClass clazz = clazzes.get(0);
        String tabTitle = ReadAction.compute(() -> clazz.isValid() ? clazz.getName() : "<invalid>");

        ApplicationManager.getApplication().invokeLater(() -> {
            // Ensure tool window is visible
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
            if (toolWindow != null) {
                toolWindow.show();
            }

            // Create new console tab on EDT
            ConsoleView console = ConsoleManager.openNewConsole(project, tabTitle);
            ConsoleManager.print(console,
                    "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                            " 🚀 JAIPilot Test Generation\n" +
                            " Class: " + tabTitle + "\n" +
                            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n",
                    ConsoleViewContentType.SYSTEM_OUTPUT);

            ProgressManager.getInstance().run(new Task.Backgroundable(
                    project,
                    "Generating tests for " + tabTitle,
                    true
            ) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    if (indicator.isCanceled()) return;

                    String qName = ReadAction.compute(() ->
                            clazz.isValid() ? clazz.getQualifiedName() : "<invalid>");

                    ApplicationManager.getApplication().invokeLater(() ->
                            ConsoleManager.print(console,
                                    "⚙️ Processing " + qName,
                                    ConsoleViewContentType.NORMAL_OUTPUT)
                    );

                    TestGenerationWorker.process(project, clazzes, console, indicator, generationType);
                }

                @Override
                public void onCancel() {
                    ApplicationManager.getApplication().invokeLater(() ->
                            ConsoleManager.print(console,
                                    "❌ JAIPilot generation cancelled by user.",
                                    ConsoleViewContentType.ERROR_OUTPUT)
                    );
                }

                @Override
                public void onSuccess() {
//                    ApplicationManager.getApplication().invokeLater(() -> {
//                        ConsoleManager.print(console,
//                                "✅ All tests generated successfully!",
//                                ConsoleViewContentType.SYSTEM_OUTPUT);
//
//
//                    });
                }
            });

        });
    }

}
