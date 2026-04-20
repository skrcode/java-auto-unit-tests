// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.service;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class ConsoleManager {

    private ConsoleManager() {}

    /**
     * Safely focuses the console tool window on the EDT.
     * This is intentionally lightweight to avoid startup crashes
     * when invoked off the UI thread by legacy callers.
     */
    public static void openTestCoverageReport(Project project) {
        if (project == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow =
                    ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
            if (toolWindow != null) {
                toolWindow.show();
            }
        });
    }

    /**
     * Opens a new console tab with a cancel button wired to the given ProgressIndicator.
     */
    public static ConsoleView openNewConsole(Project project, String title) {
        return openNewConsole(project, title, null);
    }

    public static ConsoleView openNewConsole(Project project, String title, @Nullable Runnable onClose) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
        if (toolWindow == null) return null;

        // New console instance per run
        ConsoleViewImpl consoleView = new ConsoleViewImpl(project, true);

        // ✅ Enable soft wraps so long lines wrap instead of horizontal scroll
        Editor editor = consoleView.getEditor();
        if (editor != null) {
            editor.getSettings().setUseSoftWraps(true);
        }

        // Print a header line
        consoleView.print("▶️ Starting: " + title + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);

        // --- Cancel Action ---
        AnAction cancelAction = new AnAction("Cancel JAIPilot", "Stop test generation", AllIcons.Actions.Suspend) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
//                if (indicator != null && !indicator.isCanceled()) {
//                    indicator.cancel(); // ✅ cancels the background task
//                    consoleView.print("[JAIPilot] ❌ Cancel requested by user\n", ConsoleViewContentType.ERROR_OUTPUT);
//                }
            }
        };

        DefaultActionGroup actionGroup = new DefaultActionGroup();
        AnAction runAllTests = ActionManager.getInstance().getAction("JAIPilot.Console.RunAllTests");
        if (runAllTests != null) {
            actionGroup.add(runAllTests);
        }
//        actionGroup.add(cancelAction);
        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar("JAIPilotConsoleToolbar", actionGroup, false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar.getComponent(), BorderLayout.WEST);
        panel.add(consoleView.getComponent(), BorderLayout.CENTER);

        // Add console with toolbar to tool window as a new tab
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(panel, title, true);
        if (onClose != null) {
            Disposable disposer = Disposer.newDisposable("jaipilot.console.tab");
            Disposer.register(disposer, onClose::run);
            content.setDisposer(disposer);
        }
        content.setCloseable(true);
        toolWindow.getContentManager().addContent(content);
        toolWindow.getContentManager().setSelectedContent(content);
        toolWindow.show();

        return consoleView;
    }

    public static void print(ConsoleView consoleView, String text, ConsoleViewContentType type) {
        if (consoleView == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            consoleView.print(text + "\n", type);
            if (consoleView instanceof ConsoleViewImpl impl) {
                impl.scrollToEnd();
            }
        });
    }
}
