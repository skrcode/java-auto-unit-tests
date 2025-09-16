package com.github.skrcode.javaautounittests;

import com.github.skrcode.javaautounittests.settings.JAIPilotConsoleManager;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;

/**
 * Spins up a background task that iterates over classes sequentially.
 */
public final class BulkGeneratorService {

    public static void enqueue(Project project, PsiClass clazz, @Nullable PsiDirectory testRoot) {
        String tabTitle = ReadAction.compute(() -> clazz.isValid() ? clazz.getName() : "<invalid>");

        ApplicationManager.getApplication().invokeLater(() -> {
            // Ensure tool window is visible
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("JAIPilot Console");
            if (toolWindow != null) {
                toolWindow.show();
            }

            // Create new console tab on EDT
            ConsoleView console = JAIPilotConsoleManager.openNewConsole(project, tabTitle);

            // Header log
            JAIPilotConsoleManager.print(console,
                    "▶️ Starting test generation for " + tabTitle,
                    ConsoleViewContentType.SYSTEM_OUTPUT);

            // Now kick off background task
            ProgressManager.getInstance().run(new Task.Backgroundable(
                    project,
                    "▶️ Starting test generation for " + tabTitle,
                    true
            ) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    if (indicator.isCanceled()) return;

                    String qName = ReadAction.compute(() ->
                            clazz.isValid() ? clazz.getQualifiedName() : "<invalid>");

                    ApplicationManager.getApplication().invokeLater(() ->
                            JAIPilotConsoleManager.print(console,
                                    "Processing " + qName,
                                    ConsoleViewContentType.NORMAL_OUTPUT)
                    );

                    TestGenerationWorker.process(project, clazz, console, testRoot);
                }

                @Override
                public void onCancel() {
                    ApplicationManager.getApplication().invokeLater(() ->
                            JAIPilotConsoleManager.print(console,
                                    "❌ JAIPilot generation cancelled by user.",
                                    ConsoleViewContentType.ERROR_OUTPUT)
                    );
                }

                @Override
                public void onSuccess() {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        JAIPilotConsoleManager.print(console,
                                "✅ All tests generated successfully!",
                                ConsoleViewContentType.SYSTEM_OUTPUT);

                        NotificationGroupManager.getInstance()
                                .getNotificationGroup("JAIPilot - One-Click Automatic JUnit Test Generator Feedback")
                                .createNotification(
                                        "All tests generated!",
                                        "If JAIPilot helped you, please <a href=\"https://plugins.jetbrains.com/plugin/27706-jaipilot--ai-unit-test-generator/edit/reviews/new\">leave a review</a> and ⭐️ rate it - it helps a lot!",
                                        NotificationType.INFORMATION
                                )
                                .setListener((notification, event) -> {
                                    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                        BrowserUtil.browse(event.getURL().toString());
                                        notification.expire();
                                    }
                                })
                                .notify(project);
                    });
                }
            });
        });
    }

}
