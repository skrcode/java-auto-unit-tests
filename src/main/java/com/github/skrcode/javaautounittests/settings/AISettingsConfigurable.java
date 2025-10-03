// JAIPilot Settings — with “How to use” box at top
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
import javax.swing.border.LineBorder;
import java.awt.*;
import java.net.URI;

public class AISettingsConfigurable implements Configurable {

    private final Project project;

    public AISettingsConfigurable(Project project) {
        this.project = project;
    }

    // Root + main sections
    private JPanel rootPanel;
    private JPanel jaipilotPanel;
    private JPanel modeCards;
    private CardLayout cardLayout;

    private JBPasswordField jaipilotKeyField;
    private TextFieldWithBrowseButton testDirField;

    // Common
    private JCheckBox telemetryCheck;

    private static final int GAP_BETWEEN_BLOCKS = 8;
    private static final int GAP_LABEL_TO_CONTROL = 4;
    private static final String ACCOUNT_URL = "https://www.jaipilot.com/account";

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "JAIPilot - One-Click Automatic Java Unit Test Generator";
    }

    @Override
    public @Nullable JComponent createComponent() {
        rootPanel = new JPanel(new BorderLayout());

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rootPanel.add(contentPanel, BorderLayout.NORTH);

        // ===== How to use JAIPilot box =====
        JPanel howToBox = new JPanel();
        howToBox.setLayout(new BoxLayout(howToBox, BoxLayout.Y_AXIS));
        howToBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        howToBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(200, 200, 200)), new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel howToTitle = new JLabel("After setup, you can right-click any Java class and auto-generate Tests with JAIPilot.");
        howToTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        howToBox.add(howToTitle);
        howToBox.add(Box.createVerticalStrut(4));

        contentPanel.add(howToBox);
        contentPanel.add(Box.createVerticalStrut(12));

        // ===== Header =====
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(header);
        contentPanel.add(Box.createVerticalStrut(6));

        // ===== Mode toggle =====
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        togglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ===== Common Settings =====
        JPanel commonPanel = new JPanel();
        commonPanel.setLayout(new BoxLayout(commonPanel, BoxLayout.Y_AXIS));
        commonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        telemetryCheck = new JCheckBox("Help improve JAIPilot with anonymous usage statistics");
        telemetryCheck.setToolTipText("Sends only anonymized feature usage (no source code or personal data).");
        telemetryCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        addFormBlock(commonPanel, "General:", telemetryCheck);
        contentPanel.add(commonPanel);
        contentPanel.add(Box.createVerticalStrut(8));

        // Test dir chooser
        Dimension fieldSize = new Dimension(520, 30);
        testDirField = new TextFieldWithBrowseButton();
        sizeBrowse(testDirField, fieldSize);
        testDirField.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor())
        );
        addFormBlock(contentPanel, "Select Test Root (e.g., src/test/java):", testDirField);
        contentPanel.add(Box.createVerticalStrut(8));

        // ===== Cards =====
        cardLayout = new CardLayout();
        modeCards = new JPanel(cardLayout);
        modeCards.setAlignmentX(Component.LEFT_ALIGNMENT);

        // JAIPilot panel
        jaipilotPanel = new JPanel();
        jaipilotPanel.setLayout(new BoxLayout(jaipilotPanel, BoxLayout.Y_AXIS));

        JLabel jaipilotSteps = new JLabel(
                "<html><div style='width:520px; text-align:left;'>"
                        + "<b>Quick steps</b>"
                        + "<ol style='margin-top:4px;'>"
                        + "<li>Click <i>Open Account</i> to sign in or sign up</li>"
                        + "<li>Copy your <b>License Key</b> from the Account page</li>"
                        + "<li>Paste it below</li>"
                        + "</ol>"
                        + "</div></html>"
        );
        addFormBlock(jaipilotPanel, null, jaipilotSteps);

        JButton openAccountBtn = new JButton("Open Account");
        openAccountBtn.setFocusable(false);
        openAccountBtn.addActionListener(e -> open(ACCOUNT_URL));
        JPanel accountCtaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        accountCtaRow.add(openAccountBtn);
        addFormBlock(jaipilotPanel, null, accountCtaRow);

        jaipilotKeyField = new JBPasswordField();
        sizeField(jaipilotKeyField, fieldSize);
        JPanel keyRow = new JPanel();
        keyRow.setLayout(new BoxLayout(keyRow, BoxLayout.X_AXIS));
        keyRow.add(jaipilotKeyField);

        keyRow.add(Box.createHorizontalStrut(6));
        JCheckBox showKey = new JCheckBox("Show");
        showKey.setFocusable(false);
        showKey.addActionListener(ev -> setReveal(jaipilotKeyField, showKey.isSelected()));
        keyRow.add(showKey);

        addFormBlock(jaipilotPanel, "JAIPilot License Key:", keyRow);

        JLabel tip = new JLabel(
                "<html><div style='width:520px; color:#888;'>Tip: You can always reopen <a href='" + ACCOUNT_URL + "'>jaipilot.com/account</a> to manage your key.</div></html>"
        );
        tip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tip.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { open(ACCOUNT_URL); }
        });
        addFormBlock(jaipilotPanel, null, tip);

        modeCards.add(jaipilotPanel, "JAIPilot");
        contentPanel.add(modeCards);

        // Load persisted state
        AISettings app = AISettings.getInstance();
        jaipilotKeyField.setText(app.getProKey());
        telemetryCheck.setSelected(app.isTelemetryEnabled());

        String projectTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        if (StringUtil.isEmptyOrSpaces(projectTestDir)) {
            String auto = detectTestRoot(project);
            if (!StringUtil.isEmptyOrSpaces(auto)) testDirField.setText(auto);
        } else {
            testDirField.setText(projectTestDir);
        }

//        updateModeFields();
        return rootPanel;
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

    private void sizeField(JTextField field, Dimension d) { field.setPreferredSize(d); field.setMaximumSize(d); }
    private void sizeBrowse(TextFieldWithBrowseButton b, Dimension d) { b.setPreferredSize(d); b.setMaximumSize(d); }

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

        return !StringUtil.equals(jaipilotKeyField.getText(), StringUtil.notNullize(app.proKey))
                || !StringUtil.equals(StringUtil.notNullize(testDirField.getText()), StringUtil.notNullize(projTestDir))
                || telemetryCheck.isSelected() != AISettings.getInstance().isTelemetryEnabled();
    }

    @Override
    public void apply() {
        AISettings app = AISettings.getInstance();
        app.setProKey(jaipilotKeyField.getText());
        app.setTelemetryEnabled(telemetryCheck.isSelected());

        AIProjectSettings proj = AIProjectSettings.getInstance(project);
        proj.setTestDirectory(StringUtil.notNullize(testDirField.getText()));
    }

    @Override
    public void reset() {
        AISettings.State app = AISettings.getInstance().getState();
        jaipilotKeyField.setText(StringUtil.notNullize(app.proKey));

        String projTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        testDirField.setText(StringUtil.notNullize(projTestDir));
        telemetryCheck.setSelected(AISettings.getInstance().isTelemetryEnabled());
    }

    private void open(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
    }
    private void setReveal(JPasswordField f, boolean reveal) {
        if (reveal) {
            if (f.getClientProperty("echoBackup") == null) f.putClientProperty("echoBackup", f.getEchoChar());
            f.setEchoChar((char)0);
        } else {
            Object b = f.getClientProperty("echoBackup");
            if (b instanceof Character) f.setEchoChar((Character) b);
        }
    }
}
