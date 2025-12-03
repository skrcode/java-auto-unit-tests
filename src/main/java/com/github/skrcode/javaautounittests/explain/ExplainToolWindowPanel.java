/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.explain;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

public class ExplainToolWindowPanel extends JPanel {

    private final JTextPane textPane;

    public ExplainToolWindowPanel(Project project) {
        super(new BorderLayout());

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBorder(JBUI.Borders.empty(8));

        // Use IntelliJ look & feel colors if available
        Color bg = UIManager.getColor("EditorPane.background");
        Color fg = UIManager.getColor("EditorPane.foreground");
        if (bg != null) textPane.setBackground(bg);
        if (fg != null) textPane.setForeground(fg);

        setupStyles(textPane.getStyledDocument());

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupStyles(StyledDocument doc) {
        // Base style
        Style defaultStyle = doc.addStyle("default", null);
        StyleConstants.setFontFamily(defaultStyle, "JetBrains Mono");
        StyleConstants.setFontSize(defaultStyle, 13);

        // H1
        Style h1 = doc.addStyle("h1", defaultStyle);
        StyleConstants.setBold(h1, true);
        StyleConstants.setFontSize(h1, 18);
        StyleConstants.setSpaceAbove(h1, 8);
        StyleConstants.setSpaceBelow(h1, 4);

        // H2
        Style h2 = doc.addStyle("h2", defaultStyle);
        StyleConstants.setBold(h2, true);
        StyleConstants.setFontSize(h2, 15);
        StyleConstants.setSpaceAbove(h2, 6);
        StyleConstants.setSpaceBelow(h2, 2);

        // Bullet / normal text
        Style body = doc.addStyle("body", defaultStyle);
        StyleConstants.setSpaceAbove(body, 1);
        StyleConstants.setSpaceBelow(body, 1);

        Style bullet = doc.addStyle("bullet", body);
        StyleConstants.setLeftIndent(bullet, 16);
        StyleConstants.setFirstLineIndent(bullet, -8);
    }

    public void showText(String markdownLike) {
        StyledDocument doc = textPane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) {
        }

        String[] lines = markdownLike.split("\\R");
        for (String line : lines) {
            String styleName = "body";
            String text = line;

            if (line.startsWith("## ")) {
                styleName = "h2";
                text = line.substring(3);
            } else if (line.startsWith("# ")) {
                styleName = "h1";
                text = line.substring(2);
            } else if (line.startsWith("• ")) {
                styleName = "bullet";
                text = line;
            } else if (line.startsWith("- ")) {
                styleName = "bullet";
                text = "• " + line.substring(2);
            }

            try {
                doc.insertString(doc.getLength(), text + "\n", doc.getStyle(styleName));
            } catch (BadLocationException ignored) {
            }
        }
    }
}
