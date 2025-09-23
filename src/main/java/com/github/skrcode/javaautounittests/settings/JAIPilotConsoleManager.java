package com.github.skrcode.javaautounittests.settings;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class JAIPilotConsoleManager {

    public static ConsoleView openNewConsole(Project project, String title) {
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
                JAIPilotExecutionManager.cancel();
                consoleView.print("[JAIPilot] ❌ Cancel requested by user\n", ConsoleViewContentType.ERROR_OUTPUT);
                throw new ProcessCanceledException(); // friendly cancellation
            }
        };

        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(cancelAction);
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("JAIPilotConsoleToolbar", actionGroup, false);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar.getComponent(), BorderLayout.WEST);
        panel.add(consoleView.getComponent(), BorderLayout.CENTER);

        // Add console with toolbar to tool window as a new tab
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(panel, title, true);
        toolWindow.getContentManager().addContent(content);
        toolWindow.getContentManager().setSelectedContent(content);
        toolWindow.show();

        return consoleView;
    }

    public static void print(ConsoleView consoleView, String text, ConsoleViewContentType type) {
        ApplicationManager.getApplication().invokeLater(() -> {
            consoleView.print(text + "\n", type);
            if (consoleView instanceof ConsoleViewImpl impl) {
                impl.scrollToEnd();
            }
        });
    }
}
