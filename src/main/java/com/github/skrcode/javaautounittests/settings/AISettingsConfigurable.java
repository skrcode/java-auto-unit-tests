// Compact AISettingsConfigurable.java (per-project Test Root) — JAIPilot-first UX with simple “get key” flow
package com.github.skrcode.javaautounittests.settings;

import com.github.skrcode.javaautounittests.PromptBuilder;
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
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AISettingsConfigurable implements Configurable {

    private final Project project;

    public AISettingsConfigurable(Project project) {
        this.project = project;
    }

    private JPanel rootPanel;
    private JPanel customPanel;
    private JPanel jaipilotPanel;
    private JPanel modeCards;
    private CardLayout cardLayout;

    // Modes (JAIPilot first priority, BYOK second)
    private JRadioButton jaipilotRadio;
    private JRadioButton customRadio;

    // Inputs
    private JBPasswordField geminiApiKeyField;   // BYOK
    private JBPasswordField jaipilotKeyField;    // JAIPilot

    private JComboBox<String> modelCombo;        // BYOK only
    private TextFieldWithBrowseButton testDirField;

    // Common
    private JCheckBox telemetryCheck;

    private static final int GAP_BETWEEN_BLOCKS = 8;
    private static final int GAP_LABEL_TO_CONTROL = 4;

    private static final String ACCOUNT_URL = "https://www.jaipilot.com/account";

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "JAIPilot - One-Click Automatic JUnit Test Generator";
    }

    @Override
    public @Nullable JComponent createComponent() {
        rootPanel = new JPanel(new BorderLayout());

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rootPanel.add(contentPanel, BorderLayout.NORTH);

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("Choose setup:");
        header.add(title);
        contentPanel.add(header);
        contentPanel.add(Box.createVerticalStrut(6));

        // Mode toggle — JAIPilot first (primary), BYOK second
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        togglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        jaipilotRadio = new JRadioButton("JAIPilot Key (recommended)");
        customRadio   = new JRadioButton("BYOK – Google Gemini");
        ButtonGroup group = new ButtonGroup();
        group.add(jaipilotRadio);
        group.add(customRadio);
        togglePanel.add(jaipilotRadio);
        togglePanel.add(customRadio);
        contentPanel.add(togglePanel);
        contentPanel.add(Box.createVerticalStrut(8));

        jaipilotRadio.addActionListener(e -> updateModeFields());
        customRadio.addActionListener(e -> updateModeFields());

        // ===== COMMON SETTINGS (apply to both modes) =====
        JPanel commonPanel = new JPanel();
        commonPanel.setLayout(new BoxLayout(commonPanel, BoxLayout.Y_AXIS));
        commonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        telemetryCheck = new JCheckBox("Help improve JAIPilot with anonymous usage statistics");
        telemetryCheck.setToolTipText("Sends only anonymized feature usage (no source code or personal data).");
        telemetryCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        addFormBlock(commonPanel, "General:", telemetryCheck);
        contentPanel.add(commonPanel);
        contentPanel.add(Box.createVerticalStrut(8));

        // Test dir chooser — shared for BOTH modes
        Dimension fieldSize = new Dimension(520, 30);
        testDirField = new TextFieldWithBrowseButton();
        sizeBrowse(testDirField, fieldSize);
        testDirField.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor())
        );
        addFormBlock(contentPanel, "Select Test Root (applies to all modes, e.g., src/test/java):", testDirField);
        contentPanel.add(Box.createVerticalStrut(8));

        // Cards
        cardLayout = new CardLayout();
        modeCards = new JPanel(cardLayout);
        modeCards.setAlignmentX(Component.LEFT_ALIGNMENT);

        // === JAIPilot panel (simple 3-step + field + CTA) ===
        jaipilotPanel = new JPanel();
        jaipilotPanel.setLayout(new BoxLayout(jaipilotPanel, BoxLayout.Y_AXIS));

        JLabel jaipilotSteps = new JLabel(
                "<html><div style='width:520px; text-align:left;'>"
                        + "<b>Quick steps</b>"
                        + "<ol style='margin-top:4px;'>"
                        + "<li>Click <i>Open Account</i> to sign in or sign up</li>"
                        + "<li>Copy your <b>License Key</b> from the Account page</li>"
                        + "<li>Paste it below and click <i>Save</i></li>"
                        + "</ol>"
                        + "</div></html>"
        );
        jaipilotSteps.setAlignmentX(Component.LEFT_ALIGNMENT);
        addFormBlock(jaipilotPanel, null, jaipilotSteps);

        // Open Account CTA (large, primary)
        JButton openAccountBtn = new JButton("Open Account");
        openAccountBtn.setFocusable(false);
        openAccountBtn.addActionListener(e -> open(ACCOUNT_URL));
        JPanel accountCtaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        accountCtaRow.add(openAccountBtn);
        addFormBlock(jaipilotPanel, null, accountCtaRow);

        // Key row: field + Show + Paste from clipboard
        jaipilotKeyField = new JBPasswordField();
        sizeField(jaipilotKeyField, fieldSize);
        JPanel keyRow = new JPanel();
        keyRow.setLayout(new BoxLayout(keyRow, BoxLayout.X_AXIS));
        keyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyRow.add(jaipilotKeyField);

        keyRow.add(Box.createHorizontalStrut(6));
        JCheckBox showKey = new JCheckBox("Show");
        showKey.setFocusable(false);
        showKey.addActionListener(ev -> setReveal(jaipilotKeyField, showKey.isSelected()));
        keyRow.add(showKey);

        keyRow.add(Box.createHorizontalStrut(6));
//        JButton pasteBtn = new JButton("Paste");
//        pasteBtn.setFocusable(false);
//        pasteBtn.addActionListener(ev -> pasteFromClipboard(jaipilotKeyField));
//        keyRow.add(pasteBtn);

        addFormBlock(jaipilotPanel, "JAIPilot License Key:", keyRow);

        JLabel tip = new JLabel(
                "<html><div style='width:520px; color:#888;'>Tip: You can always reopen <a href='" + ACCOUNT_URL + "'>jaipilot.com/account</a> to manage your key.</div></html>"
        );
        tip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tip.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { open(ACCOUNT_URL); }
        });
        addFormBlock(jaipilotPanel, null, tip);

        // === BYOK / Custom Gemini panel (API key + model) ===
        customPanel = new JPanel();
        customPanel.setLayout(new BoxLayout(customPanel, BoxLayout.Y_AXIS));
        geminiApiKeyField = new JBPasswordField();
        sizeField(geminiApiKeyField, fieldSize);
        JPanel geminiRow = new JPanel();
        geminiRow.setLayout(new BoxLayout(geminiRow, BoxLayout.X_AXIS));
        geminiRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        geminiRow.add(geminiApiKeyField);
        JCheckBox showGemini = new JCheckBox("Show");
        showGemini.setFocusable(false);
        showGemini.addActionListener(ev -> setReveal(geminiApiKeyField, showGemini.isSelected()));
        geminiRow.add(showGemini);

        modelCombo = new JComboBox<>(new String[]{"Loading models..."});
        sizeCombo(modelCombo, fieldSize);
        modelCombo.setEnabled(false);

        addFormBlock(customPanel, "Gemini API Key:", geminiRow);
        addFormBlock(customPanel, "Select Gemini Model:", modelCombo);

        modeCards.add(jaipilotPanel, "JAIPilot");
        modeCards.add(customPanel,   "Custom");
        contentPanel.add(modeCards);

        // Load state from persistent settings
        AISettings app = AISettings.getInstance();
        // Map old values to new UI (keep underlying storage compatible):
        // "pro" -> JAIPilot, "free" -> Custom
        String savedMode = StringUtil.notNullize(app.getMode());
        if ("pro".equalsIgnoreCase(savedMode)) {
            jaipilotRadio.setSelected(true);
        } else if ("free".equalsIgnoreCase(savedMode)) {
            customRadio.setSelected(true);
        } else {
            jaipilotRadio.setSelected(true); // default priority
        }

        geminiApiKeyField.setText(app.getOpenAiKey());  // reusing existing field
        jaipilotKeyField.setText(app.getProKey());      // reusing existing field
        telemetryCheck.setSelected(app.isTelemetryEnabled());

        // Detect project-level test root
        String projectTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        if (StringUtil.isEmptyOrSpaces(projectTestDir)) {
            String auto = detectTestRoot(project);
            if (!StringUtil.isEmptyOrSpaces(auto)) testDirField.setText(auto);
        } else {
            testDirField.setText(projectTestDir);
        }

        // Populate models for Custom/Gemini flow
        updateModeFields();
        new Thread(this::loadModelsInBackground, "JAIPilot-LoadModels").start();
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
    private void sizeCombo(JComboBox<?> combo, Dimension d) { combo.setPreferredSize(d); combo.setMaximumSize(d); }
    private void sizeBrowse(TextFieldWithBrowseButton b, Dimension d) { b.setPreferredSize(d); b.setMaximumSize(d); }

    private void updateModeFields() {
        boolean isCustom = customRadio.isSelected();
        cardLayout.show(modeCards, isCustom ? "Custom" : "JAIPilot");
        modelCombo.setEnabled(isCustom);
        rootPanel.revalidate();
        rootPanel.repaint();
    }

    private void loadModelsInBackground() {
        try {
            Map<String, List<String>> models = PromptBuilder.getModels();
            List<String> allModels = new ArrayList<>();
            models.values().forEach(allModels::addAll);
            SwingUtilities.invokeLater(() -> {
                modelCombo.removeAllItems();
                for (String model : allModels) modelCombo.addItem(model);
                String saved = StringUtil.notNullize(AISettings.getInstance().getModel());
                if (!saved.isEmpty()) modelCombo.setSelectedItem(saved);
                modelCombo.setEnabled(customRadio.isSelected());
            });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                modelCombo.removeAllItems();
                modelCombo.addItem("Error loading models");
                modelCombo.setEnabled(false);
            });
        }
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
        String selModel = (String) modelCombo.getSelectedItem();

        return !getMode().equals(StringUtil.notNullize(app.mode))
                || !StringUtil.equals(geminiApiKeyField.getText(), StringUtil.notNullize(app.openAiKey))
                || !StringUtil.equals(jaipilotKeyField.getText(), StringUtil.notNullize(app.proKey))
                || !StringUtil.equals(StringUtil.notNullize(selModel), StringUtil.notNullize(app.model))
                || !StringUtil.equals(StringUtil.notNullize(testDirField.getText()), StringUtil.notNullize(projTestDir))
                || telemetryCheck.isSelected() != AISettings.getInstance().isTelemetryEnabled();
    }

    @Override
    public void apply() {
        AISettings app = AISettings.getInstance();
        app.setMode(getMode()); // keep underlying values "Pro"/"Free" for backward-compat
        app.setOpenAiKey(geminiApiKeyField.getText());
        app.setProKey(jaipilotKeyField.getText());
        Object sel = modelCombo.getSelectedItem();
        app.setModel(sel == null ? "" : sel.toString());
        app.setTelemetryEnabled(telemetryCheck.isSelected());

        AIProjectSettings proj = AIProjectSettings.getInstance(project);
        proj.setTestDirectory(StringUtil.notNullize(testDirField.getText()));
    }

    @Override
    public void reset() {
        AISettings.State app = AISettings.getInstance().getState();
        if ("pro".equalsIgnoreCase(app.mode)) jaipilotRadio.setSelected(true);
        else if ("free".equalsIgnoreCase(app.mode)) customRadio.setSelected(true);
        else jaipilotRadio.setSelected(true); // default priority

        geminiApiKeyField.setText(StringUtil.notNullize(app.openAiKey));
        jaipilotKeyField.setText(StringUtil.notNullize(app.proKey));
        modelCombo.setSelectedItem(StringUtil.notNullize(app.model));

        String projTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        testDirField.setText(StringUtil.notNullize(projTestDir));
        telemetryCheck.setSelected(AISettings.getInstance().isTelemetryEnabled());
        updateModeFields();
    }

    // Map UI -> existing persisted values for compatibility:
    // JAIPilot -> "Pro", Custom/Gemini -> "Free"
    private String getMode() { return jaipilotRadio.isSelected() ? "Pro" : "Free"; }

    // --- tiny helpers (UX niceties) ---
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
    private void pasteFromClipboard(JTextField field) {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            String s = (String) cb.getData(DataFlavor.stringFlavor);
            if (s != null) field.setText(s.trim());
        } catch (Exception ignored) {}
    }
}
