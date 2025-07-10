package com.github.skrcode.javaautounittests;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.List;

/**
 * Spins up a background task that iterates over many classes sequentially.
 */
public final class BulkGeneratorService {

    public static void enqueue(Project project, List<PsiClass> classes, @Nullable PsiDirectory testRoot) {
        ProgressManager.getInstance().run(new Task.Backgroundable(
                project,
                "JAIPilot – Generating tests for " + classes.size() + " class(es)",
                /* cancellable = */ true      // ✅ enables red "×" cancel button
        ) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                int idx = 0;
                for (PsiClass cut : classes) {
                    if (indicator.isCanceled()) break;
                    String qName = ReadAction.compute(() ->
                            cut.isValid() ? cut.getQualifiedName() : "<invalid>");

                    indicator.setText("Processing " + qName);
                    indicator.setFraction(idx++ / (double) classes.size());
                    TestGenerationWorker.process(project, cut, indicator, testRoot);
                }
            }

            @Override
            public void onCancel() {
                // Optional: log, cleanup, or notify user
                System.out.println("JAIPilot bulk generation cancelled by user.");
            }

            @Override
            public void onSuccess() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    NotificationGroupManager.getInstance()
                            .getNotificationGroup("JAIPilot - AI Unit Test Generator Feedback")
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
    }
}
