package com.github.skrcode.javaautounittests.settings;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

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

        // Add console to tool window as a new tab
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(consoleView.getComponent(), title, true);
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
