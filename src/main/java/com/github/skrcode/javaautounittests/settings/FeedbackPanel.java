/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.application.ApplicationManager;
import javax.swing.*;
import java.awt.*;

public class FeedbackPanel extends JPanel {

    private final JLabel statusLabel = new JLabel("Was this generation helpful?");
    private final JButton thumbsUp = new JButton("👍");
    private final JButton thumbsDown = new JButton("👎");
    private boolean submitted = false;

    public FeedbackPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        add(statusLabel);
        add(thumbsUp);
        add(thumbsDown);

        thumbsUp.addActionListener(e -> handleFeedback("up"));
        thumbsDown.addActionListener(e -> handleFeedback("down"));
    }

    private void handleFeedback(String vote) {
        if (submitted) return;
        submitted = true;

        ApplicationManager.getApplication().invokeLater(() -> {
            sendFeedback(vote);
            removeAll();
            add(new JLabel("✅ Feedback submitted. Thank you!"));
            revalidate();
            repaint();
        });
    }

    private void sendFeedback(String vote) {
        // Replace with Supabase or backend call
        System.out.println("Vote: " + vote);
    }
}
