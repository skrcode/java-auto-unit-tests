// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.view;

import com.github.skrcode.javaautounittests.dto.QuotaResponse;
import com.github.skrcode.javaautounittests.service.QuotaService;
import com.github.skrcode.javaautounittests.state.AISettings;
import com.github.skrcode.javaautounittests.util.auth.JAIPilotAuthService;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AISettingsConfigurable implements Configurable {

    private final Project project;

    public AISettingsConfigurable(Project project) {
        this.project = project;
    }

    // Root + main sections
    private JPanel rootPanel;

    private JLabel creditsLabel;
    private JLabel authStateIconLabel;
    private JLabel authStatusLabel;
    private JLabel authEmailLabel;
    private JButton signInButton;
    private JButton signOutButton;

    private JCheckBox telemetryCheck;

    private static final int GAP_BETWEEN_BLOCKS = 8;
    private static final int GAP_LABEL_TO_CONTROL = 4;
    private static final String ACCOUNT_URL = "https://www.jaipilot.com/account";
    private static final Pattern URL_PATTERN = Pattern.compile("href=['\"](https?://[^'\"]+)['\"]");
    private static final Color PRIMARY_TEXT = new JBColor(new Color(35, 37, 39), new Color(220, 223, 228));
    private static final Color MUTED_TEXT = new JBColor(new Color(95, 99, 104), new Color(151, 151, 156));
    private static final Color SUCCESS_TEXT = new JBColor(new Color(30, 130, 76), new Color(121, 218, 167));
    private static final Color ERROR_TEXT = new JBColor(new Color(180, 40, 40), new Color(255, 128, 128));
    private static final Color PANEL_BORDER = new JBColor(new Color(210, 214, 220), new Color(67, 70, 75));

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "JAIPilot - One-Click AI Agent for Java Unit Testing";
    }

    @Override
    public @Nullable JComponent createComponent() {
        rootPanel = new JPanel(new BorderLayout());

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rootPanel.add(contentPanel, BorderLayout.NORTH);

        JPanel setupPanel = createSectionPanel();
        JLabel setupTitle = new JLabel("Get Started");
        setupTitle.setFont(setupTitle.getFont().deriveFont(Font.BOLD, setupTitle.getFont().getSize() + 1f));
        addFormBlock(setupPanel, null, setupTitle);

        JLabel setupBody = new JLabel(
                "<html><div style='width:560px;'>"
                        + "Right-click any Java class to generate tests with JAIPilot.<br>"
                        + "<span style='color:#3BAF66;'><b>No credit card required.</b> Free credits are included at signup.</span>"
                        + "</div></html>"
        );
        setupBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        addFormBlock(setupPanel, null, setupBody);
        contentPanel.add(setupPanel);
        contentPanel.add(Box.createVerticalStrut(10));

        JPanel accountPanel = createSectionPanel();
        JLabel accountTitle = new JLabel("Account");
        accountTitle.setFont(accountTitle.getFont().deriveFont(Font.BOLD, accountTitle.getFont().getSize() + 1f));
        addFormBlock(accountPanel, null, accountTitle);

        JLabel steps = new JLabel(
                "<html><div style='width:560px;'>"
                        + "<b>Quick steps</b>"
                        + "<ol style='margin-top:4px;'>"
                        + "<li>Click <i>Sign in to JAIPilot</i></li>"
                        + "<li>Complete sign-in in your browser, then return here</li>"
                        + "<li>Start generating tests right away with free trial credits</li>"
                        + "</ol>"
                        + "</div></html>"
        );
        addFormBlock(accountPanel, null, steps);

        authStateIconLabel = new JLabel();
        authStatusLabel = new JLabel();
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusRow.setOpaque(false);
        statusRow.add(authStateIconLabel);
        statusRow.add(authStatusLabel);
        addFormBlock(accountPanel, null, statusRow);

        authEmailLabel = new JLabel();
        authEmailLabel.setBorder(new EmptyBorder(0, 22, 0, 0));
        addFormBlock(accountPanel, null, authEmailLabel);

        signInButton = new JButton("Sign in to JAIPilot");
        signInButton.setFocusable(false);
        signInButton.addActionListener(e -> JAIPilotAuthService.startLogin(project, () -> {
            refreshAuthStatus();
            fetchAndPopulateQuotaAsync();
        }));

        signOutButton = new JButton("Sign out");
        signOutButton.setFocusable(false);
        signOutButton.addActionListener(e -> {
            JAIPilotAuthService.signOut();
            refreshAuthStatus();
            fetchAndPopulateQuotaAsync();
        });

        JButton openAccountButton = new JButton("Manage account");
        openAccountButton.setFocusable(false);
        openAccountButton.addActionListener(e -> open(ACCOUNT_URL));

        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionsRow.setOpaque(false);
        actionsRow.add(signInButton);
        actionsRow.add(signOutButton);
        actionsRow.add(openAccountButton);
        addFormBlock(accountPanel, null, actionsRow);

        JLabel accountTip = new JLabel(
                "<html><div style='width:560px; color:#8A8D91;'>"
                        + "You can always open <a href='" + ACCOUNT_URL + "'>" + ACCOUNT_URL + "</a> to manage your account."
                        + "</div></html>"
        );
        accountTip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        accountTip.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { open(ACCOUNT_URL); }
        });
        addFormBlock(accountPanel, null, accountTip);

        creditsLabel = new JLabel("Request attempts remaining: Loading...");
        creditsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        creditsLabel.setForeground(MUTED_TEXT);
        creditsLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
        creditsLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        creditsLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Matcher matcher = URL_PATTERN.matcher(creditsLabel.getText());
                if (matcher.find()) {
                    open(matcher.group(1));
                }
            }
        });
        addFormBlock(accountPanel, "Credits:", creditsLabel);

        contentPanel.add(accountPanel);
        contentPanel.add(Box.createVerticalStrut(10));

        JPanel commonPanel = createSectionPanel();
        JLabel generalTitle = new JLabel("General");
        generalTitle.setFont(generalTitle.getFont().deriveFont(Font.BOLD, generalTitle.getFont().getSize() + 1f));
        addFormBlock(commonPanel, null, generalTitle);

        telemetryCheck = new JCheckBox("Help improve JAIPilot with anonymous usage statistics");
        telemetryCheck.setToolTipText("Sends only anonymized feature usage (no source code or personal data).");
        telemetryCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        addFormBlock(commonPanel, null, telemetryCheck);
        contentPanel.add(commonPanel);

        // Load persisted state
        AISettings app = AISettings.getInstance();
        telemetryCheck.setSelected(app.isTelemetryEnabled());
        refreshAuthStatus();

        // Async quota fetch
        fetchAndPopulateQuotaAsync();

        return rootPanel;
    }

    private void fetchAndPopulateQuotaAsync() {
        if (!JAIPilotAuthService.hasConfiguredCredentials()) {
            creditsLabel.setText("Sign in to view your remaining request attempts.");
            creditsLabel.setForeground(MUTED_TEXT);
            return;
        }

        new Thread(() -> {
            try {
                QuotaResponse quota = QuotaService.fetchQuota();
                SwingUtilities.invokeLater(() -> {
                    StringBuilder sb = new StringBuilder("<html>");
                    sb.append("<b>Request attempts remaining:</b> ").append(quota.quotaRemaining);

                    String message = null;
                    if (quota.message != null && !quota.message.isEmpty()) message = quota.message;
                    if (quota.error != null && !quota.error.isEmpty()) message = quota.error;

                    if (message != null && !message.isEmpty()) {
                        String htmlMsg = escapeHtml(message).replaceAll(
                                "(https?://\\S+)",
                                "<a href='$1'>$1</a>"
                        );
                        sb.append("<br>").append(htmlMsg);
                    }

                    sb.append("</html>");
                    creditsLabel.setText(sb.toString());
                    creditsLabel.setForeground(MUTED_TEXT);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    creditsLabel.setText("Unable to fetch request attempts right now.");
                    creditsLabel.setForeground(ERROR_TEXT);
                });
            }
        }).start();
    }

    private void refreshAuthStatus() {
        String email = JAIPilotAuthService.getAuthEmail();
        boolean loggedIn = JAIPilotAuthService.hasConfiguredCredentials();
        String displayEmail = email == null || email.isBlank() ? "No email found" : email;

        authStateIconLabel.setIcon(loggedIn ? AllIcons.General.InspectionsOK : AllIcons.General.Warning);
        authStatusLabel.setText(loggedIn ? "Signed in" : "Not signed in");
        authStatusLabel.setForeground(loggedIn ? SUCCESS_TEXT : MUTED_TEXT);

        if (loggedIn) {
            authEmailLabel.setText("Account: " + displayEmail);
            Color labelForeground = UIManager.getColor("Label.foreground");
            authEmailLabel.setForeground(labelForeground != null ? labelForeground : PRIMARY_TEXT);
        } else {
            authEmailLabel.setText("Account: Sign in to view your JAIPilot email");
            authEmailLabel.setForeground(MUTED_TEXT);
        }

        signInButton.setVisible(!loggedIn);
        signInButton.setEnabled(!loggedIn);
        signOutButton.setVisible(loggedIn);
        signOutButton.setEnabled(loggedIn);
        if (signInButton.getParent() != null) {
            signInButton.getParent().revalidate();
            signInButton.getParent().repaint();
        }
    }

    private JPanel createSectionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_BORDER),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return panel;
    }

    private void addFormBlock(JPanel panel, String label, JComponent control) {
        if (panel.getComponentCount() > 0) panel.add(Box.createVerticalStrut(GAP_BETWEEN_BLOCKS));
        if (label != null && !label.isBlank()) {
            JLabel l = new JLabel(label);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(l);
            panel.add(Box.createVerticalStrut(GAP_LABEL_TO_CONTROL));
        }
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(control);
    }

    @Override
    public boolean isModified() {
        return telemetryCheck.isSelected() != AISettings.getInstance().isTelemetryEnabled();
    }

    @Override
    public void apply() {
        AISettings app = AISettings.getInstance();
        app.setTelemetryEnabled(telemetryCheck.isSelected());
        refreshAuthStatus();
        fetchAndPopulateQuotaAsync();

//        AIProjectSettings proj = AIProjectSettings.getInstance(project);
//        proj.setTestDirectory(StringUtil.notNullize(testDirField.getText()));
    }

    @Override
    public void reset() {
//        String projTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
//        testDirField.setText(StringUtil.notNullize(projTestDir));
        telemetryCheck.setSelected(AISettings.getInstance().isTelemetryEnabled());
        refreshAuthStatus();
        fetchAndPopulateQuotaAsync();
    }

    private void open(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isBlank()) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

}
