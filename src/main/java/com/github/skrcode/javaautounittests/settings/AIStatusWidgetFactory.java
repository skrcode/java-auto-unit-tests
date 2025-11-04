// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.settings;

import com.github.skrcode.javaautounittests.settings.telemetry.Telemetry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class AIStatusWidgetFactory implements StatusBarWidgetFactory {

    @Override public @NotNull String getId() { return "AIStatusWidget"; }
    @Override public @Nls @NotNull String getDisplayName() { return "JAIPilot Setup"; }
    @Override public boolean isAvailable(@NotNull Project project) { return true; }
    @Override public boolean canBeEnabledOn(@NotNull StatusBar statusBar) { return true; }
    @Override public void disposeWidget(@NotNull StatusBarWidget widget) { widget.dispose(); }
    @Override public boolean isEnabledByDefault() { return true; }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new Widget(project);
    }

    private static final class Widget implements CustomStatusBarWidget {
        private final Project project;
        private StatusBar statusBar;
        private final JPanel root = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        private final JLabel text = new JLabel("JAIPilot");
        private final JLabel icon = new JLabel();
        private final JLabel creditLabel = new JLabel("—");
        private final Timer timer;

        private Widget(Project project) {
            this.project = project;
            root.setOpaque(false);
            text.setBorder(new EmptyBorder(0, 4, 0, 0));
            root.add(text);
            root.add(icon);
            root.add(creditLabel);

            MouseAdapter clicker = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    Telemetry.uiClick("settings - widget");
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, AISettingsConfigurable.class);
                    refreshSetupIcon();
                }
            };
            root.addMouseListener(clicker);
            text.addMouseListener(clicker);
            icon.addMouseListener(clicker);
            creditLabel.addMouseListener(clicker);

            refreshSetupIcon();
            fetchAndUpdateCredits(); // initial call
            timer = new Timer(30000, e -> fetchAndUpdateCredits());
            timer.start();
        }

        @Override public @NotNull String ID() { return "AIStatusWidget"; }

        @Override public void install(@NotNull StatusBar statusBar) { this.statusBar = statusBar; }

        @Override public JComponent getComponent() { return root; }

        @Override public void dispose() { timer.stop(); }

        private void refreshSetupIcon() {
            boolean ok = isConfigured();
            icon.setIcon(ok ? AllIcons.General.InspectionsOK : AllIcons.General.Error);
            String tip = ok ? "JAIPilot: Setup complete" : "JAIPilot: Setup required — click to configure";
            root.setToolTipText(tip); text.setToolTipText(tip); icon.setToolTipText(tip);
            if (statusBar != null) statusBar.updateWidget(ID());
        }

        private boolean isConfigured() {
            AISettings s = AISettings.getInstance();
            AIProjectSettings ps = AIProjectSettings.getInstance(project);
            if (ps.getTestDirectory() == null || ps.getTestDirectory().isEmpty()) return false;
            return s.getProKey() != null && !s.getProKey().isBlank();
        }

        private void fetchAndUpdateCredits() {
            SwingUtilities.invokeLater(() -> creditLabel.setText(fetchCreditsText()));
            if (statusBar != null) statusBar.updateWidget(ID());
        }

        private String fetchCreditsText() {
//            try {
                return " " + batteryEmoji(45) + " $" + 45 + "%";
//                URL url = new URL("https://api.jaipilot.com/user/credit_percent"); // replace with real endpoint
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setConnectTimeout(3000);
//                conn.setReadTimeout(3000);
//                conn.setRequestMethod("GET");
//
//                if (conn.getResponseCode() == 200) {
//                    try (Scanner sc = new Scanner(conn.getInputStream())) {
//                        String val = sc.nextLine().trim();
//                        int percent = Integer.parseInt(val);
//                        return " " + batteryEmoji(percent) + " $" + percent + "%";
//                    }
//                }
//            } catch (Exception e) {
//                return " ⚡ $—";
//            }
//            return " ⚡ $—";
        }

        private String batteryEmoji(int percent) {
            if (percent >= 80) return "🔋";
            if (percent >= 40) return "🔋";
            if (percent >= 10) return "🪫";
            return "⚡";
        }
    }
}
