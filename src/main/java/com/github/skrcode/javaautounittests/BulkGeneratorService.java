package com.github.skrcode.javaautounittests;

import com.github.skrcode.javaautounittests.settings.JAIPilotConsoleManager;
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
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            JAIPilotConsoleManager.print(console,
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
                            JAIPilotConsoleManager.print(console,
                                    "⚙️ Processing " + qName,
                                    ConsoleViewContentType.NORMAL_OUTPUT)
                    );

                    TestGenerationWorker.process(project, clazz, console, testRoot, indicator);
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
//                    ApplicationManager.getApplication().invokeLater(() -> {
//                        JAIPilotConsoleManager.print(console,
//                                "✅ All tests generated successfully!",
//                                ConsoleViewContentType.SYSTEM_OUTPUT);


//                    });
                }
            });

        });
    }

}
