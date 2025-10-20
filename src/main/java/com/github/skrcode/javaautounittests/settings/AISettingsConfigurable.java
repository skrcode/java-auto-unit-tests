package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPasswordField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;

public class AISettingsConfigurable implements Configurable {
    private final Project project;
    private JPanel root;
    private JBPasswordField licenseField;
    private TextFieldWithBrowseButton testDirField;
    private JCheckBox telemetryCheck;

    private static final String ACCOUNT_URL = "https://www.jaipilot.com/account";

    public AISettingsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "JAIPilot – AI Test Generator";
    }

    @Override
    public @Nullable JComponent createComponent() {
        root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(14, 20, 14, 20));

        // === Title ===
        JLabel title = new JLabel("<html><div style='font-size:14px;'><b>JAIPilot — One-Click AI Unit Test Generator</b></div>"
                + "<div style='color:#888;font-size:small;'>Right-click any Java class → Generate Tests</div></html>");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(12));

        // === Subtle note ===
        JLabel note = new JLabel("<html><div style='color:#9aa0a6;font-size:small;width:520px;'>"
                + "Free credits are automatically available — no key required.<br>"
                + "After your trial, add your license key below to continue."
                + "</div></html>");
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(note);
        root.add(Box.createVerticalStrut(16));

        // === Test root folder ===
        JLabel testDirLabel = new JLabel("Test root folder (e.g., src/test/java):");
        testDirLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(testDirLabel);
        root.add(Box.createVerticalStrut(6));

        testDirField = new TextFieldWithBrowseButton();
        testDirField.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor())
        );
        Dimension fieldSize = new Dimension(520, 30);
        testDirField.setMaximumSize(fieldSize);
        testDirField.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(testDirField);
        root.add(Box.createVerticalStrut(18));

        // === License key ===
        JLabel keyLabel = new JLabel("License Key (only after free trial):");
        keyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(keyLabel);
        root.add(Box.createVerticalStrut(6));

        licenseField = new JBPasswordField();
        licenseField.setMaximumSize(fieldSize);
        licenseField.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(licenseField);
        root.add(Box.createVerticalStrut(8));

        JButton manageBtn = new JButton("Manage License");
        manageBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        manageBtn.addActionListener(e -> open(ACCOUNT_URL));
        root.add(manageBtn);
        root.add(Box.createVerticalStrut(18));

        // === Telemetry ===
        telemetryCheck = new JCheckBox("Send anonymous usage statistics");
        telemetryCheck.setToolTipText("Helps improve JAIPilot (no source code or personal data).");
        telemetryCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(telemetryCheck);

        // === Load persisted state ===
        AISettings app = AISettings.getInstance();
        licenseField.setText(app.getProKey());
        telemetryCheck.setSelected(app.isTelemetryEnabled());

        String projectTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        if (StringUtil.isEmptyOrSpaces(projectTestDir)) {
            String auto = detectTestRoot(project);
            if (!StringUtil.isEmptyOrSpaces(auto)) testDirField.setText(auto);
        } else {
            testDirField.setText(projectTestDir);
        }

        return root;
    }



    private String detectTestRoot(Project project) {
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
            if (ProjectFileIndex.getInstance(project).isInTestSourceContent(root)) {
                return root.getPath();
            }
        }
        return "";
    }

    @Override
    public boolean isModified() {
        AISettings.State app = AISettings.getInstance().getState();
        String projTestDir = AIProjectSettings.getInstance(project).getTestDirectory();

        return !StringUtil.equals(licenseField.getText(), StringUtil.notNullize(app.proKey))
                || !StringUtil.equals(StringUtil.notNullize(testDirField.getText()), StringUtil.notNullize(projTestDir))
                || telemetryCheck.isSelected() != AISettings.getInstance().isTelemetryEnabled();
    }

    @Override
    public void apply() {
        AISettings app = AISettings.getInstance();
        app.setProKey(licenseField.getText());
        app.setTelemetryEnabled(telemetryCheck.isSelected());

        AIProjectSettings proj = AIProjectSettings.getInstance(project);
        proj.setTestDirectory(StringUtil.notNullize(testDirField.getText()));
    }

    @Override
    public void reset() {
        AISettings.State app = AISettings.getInstance().getState();
        licenseField.setText(StringUtil.notNullize(app.proKey));
        testDirField.setText(StringUtil.notNullize(AIProjectSettings.getInstance(project).getTestDirectory()));
        telemetryCheck.setSelected(AISettings.getInstance().isTelemetryEnabled());
    }

    private void open(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
    }
}
