// Compact AISettingsConfigurable.java (per-project Test Root) — MINIMAL & EVERGREEN — KEY ABOVE INFO
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
    private JPanel freePanel;
    private JPanel proPanel;
    private JPanel modeCards;
    private CardLayout cardLayout;

    private JRadioButton freeRadio;
    private JRadioButton proRadio;

    private JBPasswordField freeApiKeyField;
    private JBPasswordField proKeyField;

    private JComboBox<String> modelCombo;
    private TextFieldWithBrowseButton testDirField;

    private JPanel proInstructionsPanel;
    private JPanel freeCreditsCard; // <-- shown only in "Free" mode

    private JCheckBox telemetryCheck;      // common (Free & Pro)
//    private JCheckBox thinkingModeCheck;   // Free only

    private static final int GAP_BETWEEN_BLOCKS = 8;
    private static final int GAP_LABEL_TO_CONTROL = 4;

    // Use exact link as requested
    private static final String PRICING_URL = "https://www.jaipilot.com/pricing";
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

        // Header + direct link to pricing
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("Select Plan:");
        JButton whatsInPro = new JButton("What’s in Pro?");
        whatsInPro.addActionListener(e -> open(PRICING_URL));
        header.add(title);
        header.add(whatsInPro);
        contentPanel.add(header);
        contentPanel.add(Box.createVerticalStrut(6));

        // Plan toggle
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        togglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        freeRadio = new JRadioButton("Free");
        proRadio = new JRadioButton("Pro");
        ButtonGroup group = new ButtonGroup();
        group.add(freeRadio);
        group.add(proRadio);
        togglePanel.add(freeRadio);
        togglePanel.add(proRadio);
        contentPanel.add(togglePanel);
        contentPanel.add(Box.createVerticalStrut(8));

        freeRadio.addActionListener(e -> updateModeFields());
        proRadio.addActionListener(e -> updateModeFields());

        // 🔔 Free credits card — placed UNDER the radio buttons; only visible in "Free" mode
        freeCreditsCard = new JPanel();
        freeCreditsCard.setLayout(new BoxLayout(freeCreditsCard, BoxLayout.Y_AXIS));
        freeCreditsCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        freeCreditsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JLabel freeCreditsText = new JLabel(
                "<html><div style='width:520px; text-align:left;'>"
                        + "If you don’t have a Gemini key and want to try out JAIPilot — you have <b>Pro free credits</b> available. "
                        + "Once you sign up, go to the <i>Account</i> page to find your <b>license key</b>, then paste this key under Pro settings. "
                        + "You are eligible for a <b>Pro free trial</b> (no card required)."
                        + "</div></html>"
        );

        freeCreditsText.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel freeCreditsCtas = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton getFreeBtn = new JButton("Start Free Trial (instant, no card required)");
        getFreeBtn.addActionListener(e -> open(PRICING_URL)); // open pricing
        freeCreditsCtas.add(getFreeBtn);
        freeCreditsCtas.setAlignmentX(Component.LEFT_ALIGNMENT);
        freeCreditsCard.add(freeCreditsText);
        freeCreditsCard.add(Box.createVerticalStrut(6));
        freeCreditsCard.add(freeCreditsCtas);
        contentPanel.add(freeCreditsCard); // <-- under radio buttons
        contentPanel.add(Box.createVerticalStrut(8));

        // ===== COMMON SETTINGS (apply to both Free & Pro) =====
        JPanel commonPanel = new JPanel();
        commonPanel.setLayout(new BoxLayout(commonPanel, BoxLayout.Y_AXIS));
        commonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        telemetryCheck = new JCheckBox(
                "Help improve JAIPilot with anonymous usage statistics"
        );
        telemetryCheck.setToolTipText(
                "Sends only anonymized feature usage (no source code or personal data). " +
                        "This helps us improve performance, fix issues faster, and keep free credits available."
        );
        telemetryCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        addFormBlock(commonPanel, "General:", telemetryCheck);

        // Thinking mode (Free-only) — visibility toggled by updateModeFields()
//        thinkingModeCheck = new JCheckBox("Thinking mode (slower, better, more tokens usage)");
//        thinkingModeCheck.setToolTipText("Extra reasoning steps for higher-quality tests. Only available in Free mode.");
//        thinkingModeCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
//        addFormBlock(commonPanel, null, thinkingModeCheck);

        contentPanel.add(commonPanel);
        contentPanel.add(Box.createVerticalStrut(8));

        // Test dir chooser — shared for BOTH Free & Pro
        Dimension fieldSize = new Dimension(520, 30);
        testDirField = new TextFieldWithBrowseButton();
        sizeBrowse(testDirField, fieldSize);
        testDirField.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor())
        );
        addFormBlock(contentPanel, "Select Test Root (applies to Free & Pro, e.g., src/test/java):", testDirField);
        contentPanel.add(Box.createVerticalStrut(8));

        // Cards
        cardLayout = new CardLayout();
        modeCards = new JPanel(cardLayout);
        modeCards.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Free panel
        freePanel = new JPanel();
        freePanel.setLayout(new BoxLayout(freePanel, BoxLayout.Y_AXIS));
        freeApiKeyField = new JBPasswordField();
        sizeField(freeApiKeyField, fieldSize);
        modelCombo = new JComboBox<>(new String[]{"Loading models..."});
        sizeCombo(modelCombo, fieldSize);
        modelCombo.setEnabled(false);
        addFormBlock(freePanel, "Gemini API Key:", freeApiKeyField);
        addFormBlock(freePanel, "Select Gemini Model:", modelCombo);

        // Pro panel
        proPanel = new JPanel();
        proPanel.setLayout(new BoxLayout(proPanel, BoxLayout.Y_AXIS));

        // Pro key input at the top
        proKeyField = new JBPasswordField();
        sizeField(proKeyField, fieldSize);
        JPanel proInput = new JPanel();
        proInput.setLayout(new BoxLayout(proInput, BoxLayout.X_AXIS));
        proInput.setAlignmentX(Component.LEFT_ALIGNMENT);
        proInput.add(proKeyField);
        addFormBlock(proPanel, "JAIPilot Pro Key:", proInput);

        // Upgrade / Account buttons
        JPanel ctas = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton upgradeBtn = new JButton("Upgrade to Pro");
        upgradeBtn.addActionListener(e -> open(PRICING_URL));
        JButton accountBtn = new JButton("Open Account");
        accountBtn.addActionListener(e -> open(ACCOUNT_URL));
        ctas.add(upgradeBtn);
        ctas.add(accountBtn);
        addFormBlock(proPanel, null, ctas);

        // Evergreen Pro pitch (now at bottom)
        JPanel proCard = new JPanel();
        proCard.setLayout(new BoxLayout(proCard, BoxLayout.Y_AXIS));
        proCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        JLabel proPitch = new JLabel(
                "<html><div style='width:520px; text-align:left;'>"
                        + "<b>Pro</b> — Managed, usage-based test generation. No API setup. Faster, smarter tests.<br>"
                        + "<a href='" + PRICING_URL + "'>See benefits & pricing</a>."
                        + "</div></html>"
        );
        proPitch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        proPitch.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { open(PRICING_URL); }
        });
        addFormBlock(proPanel, null, proPitch);

        // How to get key
        addFormBlock(proPanel, null, getProInstructions());

        modeCards.add(freePanel, "Free");
        modeCards.add(proPanel, "Pro");
        contentPanel.add(modeCards);

        // Load state
        AISettings app = AISettings.getInstance();
        if ("pro".equalsIgnoreCase(app.getMode())) proRadio.setSelected(true); else freeRadio.setSelected(true);
        freeApiKeyField.setText(app.getOpenAiKey());
        proKeyField.setText(app.getProKey());

        telemetryCheck.setSelected(app.isTelemetryEnabled());
//        thinkingModeCheck.setSelected(app.isThinkingMode());

        // Detect project-level test root
        String projectTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        if (StringUtil.isEmptyOrSpaces(projectTestDir)) {
            String auto = detectTestRoot(project);
            if (!StringUtil.isEmptyOrSpaces(auto)) {
                testDirField.setText(auto);
            }
        } else {
            testDirField.setText(projectTestDir);
        }

        // Models populate
        updateModeFields(); // sets card + toggles freeCreditsCard visibility
        new Thread(this::loadModelsInBackground, "JAIPilot-LoadModels").start();
        return rootPanel;
    }

    private JPanel getProInstructions() {
        proInstructionsPanel = new JPanel();
        proInstructionsPanel.setLayout(new BoxLayout(proInstructionsPanel, BoxLayout.Y_AXIS));
        proInstructionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel instructions = new JLabel("<html><div style='width:520px; text-align:left;'>"
                + "<b>Get your JAIPilot Pro Key:</b><br>"
                + "1) Visit <a href='" + PRICING_URL + "'>" + PRICING_URL + "</a> (Sign up to activate free credits — no card required)<br>"
                + "2) After signup, open <a href='" + ACCOUNT_URL + "'>Account</a> — your License Key will be shown there<br>"
                + "</div></html>");
        instructions.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        instructions.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { open(PRICING_URL); }
        });
        proInstructionsPanel.add(instructions);
        return proInstructionsPanel;
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
        boolean isFree = freeRadio.isSelected();
        cardLayout.show(modeCards, isFree ? "Free" : "Pro");
        modelCombo.setEnabled(isFree);
        if (freeCreditsCard != null) freeCreditsCard.setVisible(isFree); // <-- only show in Free mode
//        if (thinkingModeCheck != null) thinkingModeCheck.setVisible(isFree); // <-- only Free
        rootPanel.revalidate(); rootPanel.repaint();
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
                modelCombo.setEnabled(freeRadio.isSelected());
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
                || !StringUtil.equals(freeApiKeyField.getText(), StringUtil.notNullize(app.openAiKey))
                || !StringUtil.equals(proKeyField.getText(), StringUtil.notNullize(app.proKey))
                || !StringUtil.equals(StringUtil.notNullize(selModel), StringUtil.notNullize(app.model))
                || !StringUtil.equals(StringUtil.notNullize(testDirField.getText()), StringUtil.notNullize(projTestDir))
                || telemetryCheck.isSelected() != AISettings.getInstance().isTelemetryEnabled();
//                || thinkingModeCheck.isSelected() != AISettings.getInstance().isThinkingMode();
    }

    @Override
    public void apply() {
        AISettings app = AISettings.getInstance();
        app.setMode(getMode());
        app.setOpenAiKey(freeApiKeyField.getText());
        app.setProKey(proKeyField.getText());
        Object sel = modelCombo.getSelectedItem();
        app.setModel(sel == null ? "" : sel.toString());
        app.setTelemetryEnabled(telemetryCheck.isSelected());
//        app.setThinkingMode(thinkingModeCheck.isSelected());

        AIProjectSettings proj = AIProjectSettings.getInstance(project);
        proj.setTestDirectory(StringUtil.notNullize(testDirField.getText()));
    }

    @Override
    public void reset() {
        AISettings.State app = AISettings.getInstance().getState();
        if ("pro".equalsIgnoreCase(app.mode)) proRadio.setSelected(true); else freeRadio.setSelected(true);
        freeApiKeyField.setText(StringUtil.notNullize(app.openAiKey));
        proKeyField.setText(StringUtil.notNullize(app.proKey));
        modelCombo.setSelectedItem(StringUtil.notNullize(app.model));

        String projTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        testDirField.setText(StringUtil.notNullize(projTestDir));
        telemetryCheck.setSelected(AISettings.getInstance().isTelemetryEnabled());
//        thinkingModeCheck.setSelected(AISettings.getInstance().isThinkingMode());
        updateModeFields();
    }

    private String getMode() { return proRadio.isSelected() ? "Pro" : "Free"; }

    private void open(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
    }
}
