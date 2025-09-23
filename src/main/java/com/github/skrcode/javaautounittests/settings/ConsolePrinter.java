package com.github.skrcode.javaautounittests.settings;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ConsolePrinter {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static void doPrint(ConsoleView consoleView, String prefix, String text, ConsoleViewContentType type) {
        if (consoleView == null) return;

        String timestamp = "[" + LocalTime.now().format(TIME_FMT) + "]";
        String msg = String.format("%s %s %s%n", timestamp, prefix, text);

        ApplicationManager.getApplication().invokeLater(() -> {
            consoleView.print(msg, type);
            if (consoleView instanceof ConsoleViewImpl impl) {
                impl.scrollToEnd();
            }
        });
    }

    // --- Section headers ---
    public static void section(ConsoleView consoleView, String title) {
        String line = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        doPrint(consoleView, "", "\n" + line + "\n🔷 " + title + "\n" + line, ConsoleViewContentType.USER_INPUT);
    }

    // --- Standard levels ---
    public static void info(ConsoleView consoleView, String text) {
        doPrint(consoleView, "ℹ️ INFO ", text, ConsoleViewContentType.LOG_INFO_OUTPUT);
    }

    public static void success(ConsoleView consoleView, String text) {
        doPrint(consoleView, "✅ DONE ", text, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    public static void warn(ConsoleView consoleView, String text) {
        doPrint(consoleView, "⚠️ WARN ", text, ConsoleViewContentType.LOG_WARNING_OUTPUT);
    }

    public static void error(ConsoleView consoleView, String text) {
        doPrint(consoleView, "❌ FAIL ", text, ConsoleViewContentType.LOG_ERROR_OUTPUT);
    }

    public static void system(ConsoleView consoleView, String text) {
        doPrint(consoleView, "⚙️ SYS  ", text, ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    public static void detail(ConsoleView consoleView, String text) {
        doPrint(consoleView, "   •", text, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    public static void plain(ConsoleView consoleView, String text) {
        doPrint(consoleView, "   ", text, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    // --- Progress simulation ---
    public static void progress(ConsoleView consoleView, String activity, int percent) {
        String bar = makeProgressBar(percent);
        doPrint(consoleView, "⏳ " + activity, bar, ConsoleViewContentType.LOG_VERBOSE_OUTPUT);
    }

    private static String makeProgressBar(int percent) {
        int total = 20;
        int filled = (percent * total) / 100;
        return "[" + "█".repeat(filled) + " ".repeat(total - filled) + "] " + percent + "%";
    }

    /**
     * Print each new line of code as it comes in.
     * Keeps incremental line numbers across chunks.
     */
    public static int printLine(ConsoleView consoleView, int currentLine, String line) {
        if (consoleView == null || line == null) return currentLine;

        int nextLine = currentLine + 1;

        ApplicationManager.getApplication().invokeLater(() -> {
            String timestamp = "[" + LocalTime.now().format(TIME_FMT) + "]";
            String numbered = String.format("%s %3d | %s%n",
                    timestamp, nextLine, line);
            consoleView.print(numbered, ConsoleViewContentType.NORMAL_OUTPUT);

            if (consoleView instanceof ConsoleViewImpl impl) {
                impl.scrollToEnd();
            }
        });

        return nextLine;
    }

    // --- Print code with line numbers ---
    public static void codeBlock(ConsoleView consoleView, List<String> lines) {
        if (consoleView == null || lines == null) return;

        ApplicationManager.getApplication().invokeLater(() -> {
            consoleView.print("\n📄 CODE SNIPPET:\n", ConsoleViewContentType.SYSTEM_OUTPUT);

            for (int i = 0; i < lines.size(); i++) {
                String timestamp = "[" + LocalTime.now().format(TIME_FMT) + "]";
                String numbered = String.format("%s %3d | %s%n",
                        timestamp, i + 1, lines.get(i));
                consoleView.print(numbered, ConsoleViewContentType.NORMAL_OUTPUT);
            }

            if (consoleView instanceof ConsoleViewImpl impl) {
                impl.scrollToEnd();
            }
        });
    }
}
