// Pixel-perfect aligned AISettingsConfigurable.java (fixed CardLayout issue)
package com.github.skrcode.javaautounittests.settings;

import com.github.skrcode.javaautounittests.PromptBuilder;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBPasswordField;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AISettingsConfigurable implements Configurable {

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

    private JLabel validationFeedback;
    private JPanel proInstructionsPanel;

    private Boolean isProKeyValidated = null;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "JAIPilot - AI Unit Test Generator";
    }

    @Override
    public @Nullable JComponent createComponent() {
        rootPanel = new JPanel(new BorderLayout());
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rootPanel.add(contentPanel, BorderLayout.NORTH);

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        togglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        togglePanel.add(new JLabel("Select Plan:"));
        freeRadio = new JRadioButton("Free");
        proRadio = new JRadioButton("Pro");
        ButtonGroup group = new ButtonGroup();
        group.add(freeRadio); group.add(proRadio);
        togglePanel.add(freeRadio); togglePanel.add(proRadio);
        contentPanel.add(togglePanel);
        freeRadio.addActionListener(e -> updateModeFields());
        proRadio.addActionListener(e -> updateModeFields());

        cardLayout = new CardLayout();
        modeCards = new JPanel(cardLayout);
        modeCards.setAlignmentX(Component.LEFT_ALIGNMENT);

        Dimension fieldSize = new Dimension(520, 30);

        freePanel = new JPanel();
        freePanel.setLayout(new BoxLayout(freePanel, BoxLayout.Y_AXIS));
        freeApiKeyField = new JBPasswordField();
        sizeField(freeApiKeyField, fieldSize);
        modelCombo = new JComboBox<>(new String[]{"Loading models..."});
        sizeCombo(modelCombo, fieldSize);
        modelCombo.setEnabled(false);
        addFormBlock(freePanel, "Gemini API Key:", freeApiKeyField);
        addFormBlock(freePanel, "Select Gemini Model:", modelCombo);

        proPanel = new JPanel();
        proPanel.setLayout(new BoxLayout(proPanel, BoxLayout.Y_AXIS));
        proKeyField = new JBPasswordField();
        sizeField(proKeyField, fieldSize);
        JButton validateKeyBtn = new JButton("Validate Key");
        validationFeedback = new JLabel(" ");

        validateKeyBtn.addActionListener(e -> {
            isProKeyValidated = (proKeyField.getPassword().length > 0);
            validationFeedback.setForeground(isProKeyValidated ? new Color(0,128,0) : Color.RED);
            validationFeedback.setText(isProKeyValidated ? "✅ Valid Key" : "❌ Invalid Key");
            updateModeFields();
        });

        JPanel proInput = new JPanel();
        proInput.setLayout(new BoxLayout(proInput, BoxLayout.X_AXIS));
        proInput.setAlignmentX(Component.LEFT_ALIGNMENT);
        proInput.add(proKeyField);
        proInput.add(Box.createHorizontalStrut(10));
        proInput.add(validateKeyBtn);

        addFormBlock(proPanel, "JAIPilot Pro Key:", proInput);
        addFormBlock(proPanel, "", validationFeedback);
        addFormBlock(proPanel, " ", getProInstructions());
        addFormBlock(proPanel, "Select Gemini Model:", (JComponent) Box.createRigidArea(new Dimension(0, 30)));

        modeCards.add(freePanel, "free");
        modeCards.add(proPanel, "pro");
        contentPanel.add(modeCards);

        testDirField = new TextFieldWithBrowseButton();
        sizeBrowse(testDirField, fieldSize);
        testDirField.addBrowseFolderListener(new TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFolderDescriptor()));
        addFormBlock(contentPanel, "Select Test Root (e.g., src/test/java):", testDirField);

        if ("pro".equals(AISettings.getInstance().getMode())) proRadio.setSelected(true); else freeRadio.setSelected(true);
        isProKeyValidated = AISettings.getInstance().isProKeyValidated();
        if (isProKeyValidated != null)
            validationFeedback.setText(isProKeyValidated ? "✅ Valid Key" : "❌ Invalid Key");

        updateModeFields();
        new Thread(this::loadModelsInBackground).start();
        return rootPanel;
    }

    private JPanel getProInstructions() {
        proInstructionsPanel = new JPanel();
        proInstructionsPanel.setLayout(new BoxLayout(proInstructionsPanel, BoxLayout.Y_AXIS));
        proInstructionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel instructions = new JLabel("<html><div style='width:480px;text-align:left'>"
                + "<b>How to get your JAIPilot Pro Key:</b><br>"
                + "1. Visit <a href='https://jaipilot.vercel.app/pricing'>https://jaipilot.vercel.app/pricing</a>.<br>"
                + "2. Complete payment for Pro.<br>"
                + "3. Go to Account page.<br>"
                + "4. Copy your Account Key here." + "</div></html>");
        instructions.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        instructions.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                try { Desktop.getDesktop().browse(new URI("https://jaipilot.vercel.app/pricing")); } catch (Exception ignored) {}
            }
        });
        proInstructionsPanel.add(instructions);
        return proInstructionsPanel;
    }

    private void addFormBlock(JPanel panel, String label, JComponent control) {
        panel.add(Box.createVerticalStrut(8));
        JLabel l = new JLabel(label);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(l);
        panel.add(Box.createVerticalStrut(2));
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(control);
    }

    private void sizeField(JTextField field, Dimension d) { field.setPreferredSize(d); field.setMaximumSize(d); }
    private void sizeCombo(JComboBox<?> combo, Dimension d) { combo.setPreferredSize(d); combo.setMaximumSize(d); }
    private void sizeBrowse(TextFieldWithBrowseButton b, Dimension d) { b.setPreferredSize(d); b.setMaximumSize(d); }

    private void updateModeFields() {
        boolean isFree = freeRadio.isSelected();
        cardLayout.show(modeCards, isFree ? "free" : "pro");
        modelCombo.setEnabled(isFree);
        if (proInstructionsPanel != null)
            proInstructionsPanel.setVisible(!Boolean.TRUE.equals(isProKeyValidated));
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
                String saved = AISettings.getInstance().getModel();
                if (saved != null && allModels.contains(saved)) modelCombo.setSelectedItem(saved);
            });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                modelCombo.removeAllItems();
                modelCombo.addItem("Error loading models");
                modelCombo.setEnabled(false);
            });
        }
    }

    @Override
    public boolean isModified() {
        AISettings.State s = AISettings.getInstance().getState();
        return !getMode().equals(s.mode)
                || !freeApiKeyField.getText().equals(s.openAiKey)
                || !proKeyField.getText().equals(s.proKey)
                || !testDirField.getText().equals(s.testDirectory)
                || !Objects.equals(modelCombo.getSelectedItem(), s.model)
                || !Objects.equals(isProKeyValidated, s.proKeyValidated);
    }

    @Override
    public void apply() {
        AISettings s = AISettings.getInstance();
        s.setMode(getMode());
        s.setOpenAiKey(freeApiKeyField.getText());
        s.setProKey(proKeyField.getText());
        s.setTestDirectory(testDirField.getText());
        s.setModel((String) modelCombo.getSelectedItem());
        s.setProKeyValidated(isProKeyValidated);
    }

    @Override
    public void reset() {
        AISettings.State s = AISettings.getInstance().getState();
        if ("pro".equals(s.mode)) proRadio.setSelected(true); else freeRadio.setSelected(true);
        freeApiKeyField.setText(s.openAiKey);
        proKeyField.setText(s.proKey);
        testDirField.setText(s.testDirectory);
        modelCombo.setSelectedItem(s.model);
        isProKeyValidated = s.proKeyValidated;
        if (isProKeyValidated != null)
            validationFeedback.setText(isProKeyValidated ? "✅ Valid Key" : "❌ Invalid Key");
        else validationFeedback.setText(" ");
        updateModeFields();
    }

    private String getMode() { return proRadio.isSelected() ? "pro" : "free"; }
}
