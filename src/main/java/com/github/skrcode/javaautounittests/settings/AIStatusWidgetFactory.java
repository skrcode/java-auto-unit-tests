package com.github.skrcode.javaautounittests.settings;

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

    // IMPORTANT: enable by default (newer IDEs leave factories off otherwise)
    @Override public boolean isEnabledByDefault() { return true; }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new Widget(project);
    }

    private static final class Widget implements CustomStatusBarWidget {
        private final Project project;
        private StatusBar statusBar;

        // Build UI up-front so getComponent() always has a real node
        private final JPanel root = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        private final JLabel text = new JLabel("JAIPilot");
        private final JLabel icon = new JLabel();

        private Widget(Project project) {
            this.project = project;
            root.setOpaque(false);
            text.setBorder(new EmptyBorder(0, 4, 0, 0));
            root.add(text);
            root.add(icon);

            MouseAdapter clicker = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, AISettingsConfigurable.class);
                    refresh();
                }
            };
            root.addMouseListener(clicker);
            text.addMouseListener(clicker);
            icon.addMouseListener(clicker);

            refresh();
        }

        @Override public @NotNull String ID() { return "AIStatusWidget"; }

        @Override public void install(@NotNull StatusBar statusBar) {
            this.statusBar = statusBar;
        }

        @Override public JComponent getComponent() { return root; }

        @Override public void dispose() { /* nothing */ }

        private void refresh() {
            boolean ok = isConfigured();
            icon.setIcon(ok ? AllIcons.General.InspectionsOK : AllIcons.General.Error);
            String tip = ok ? "JAIPilot: Setup complete" : "JAIPilot: Setup required — click to configure";
            root.setToolTipText(tip); text.setToolTipText(tip); icon.setToolTipText(tip);
            if (statusBar != null) statusBar.updateWidget(ID());
        }

        private boolean isConfigured() {
            AISettings s = AISettings.getInstance();
            AIProjectSettings ps = AIProjectSettings.getInstance(project);
            String mode = s.getMode() == null ? "" : s.getMode().trim();
            if (ps.getTestDirectory() == null || ps.getTestDirectory().isEmpty()) return false;
            if ("Pro".equalsIgnoreCase(mode)) {
                return s.getProKey() != null && !s.getProKey().isBlank();
            } else {
                return (s.getOpenAiKey() != null && !s.getOpenAiKey().isBlank());
            }
        }

    }
}
