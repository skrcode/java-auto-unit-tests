// Compact AISettingsConfigurable.java (per-project Test Root)
package com.github.skrcode.javaautounittests.settings;

import com.github.skrcode.javaautounittests.PromptBuilder;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
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
import java.util.Objects;

public class AISettingsConfigurable implements Configurable {

    private final Project project; // NEW

    public AISettingsConfigurable(Project project) { // Project-scoped configurable
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

    private static final int GAP_BETWEEN_BLOCKS = 8;
    private static final int GAP_LABEL_TO_CONTROL = 4;

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

        // Plan toggle
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        togglePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        togglePanel.add(new JLabel("Select Plan:"));
        freeRadio = new JRadioButton("Free");
        proRadio = new JRadioButton("Pro");
        ButtonGroup group = new ButtonGroup();
        group.add(freeRadio); group.add(proRadio);
        togglePanel.add(freeRadio); togglePanel.add(proRadio);
        contentPanel.add(togglePanel);
        contentPanel.add(Box.createVerticalStrut(8));

        freeRadio.addActionListener(e -> updateModeFields());
        proRadio.addActionListener(e -> updateModeFields());

        // Cards
        cardLayout = new CardLayout();
        modeCards = new JPanel(cardLayout);
        modeCards.setAlignmentX(Component.LEFT_ALIGNMENT);

        Dimension fieldSize = new Dimension(520, 30);

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
        proKeyField = new JBPasswordField();
        sizeField(proKeyField, fieldSize);

        JPanel proInput = new JPanel();
        proInput.setLayout(new BoxLayout(proInput, BoxLayout.X_AXIS));
        proInput.setAlignmentX(Component.LEFT_ALIGNMENT);
        proInput.add(proKeyField);

        addFormBlock(proPanel, "JAIPilot Pro Key:", proInput);
        addFormBlock(proPanel, null, getProInstructions());

        modeCards.add(freePanel, "Free");
        modeCards.add(proPanel, "Pro");
        contentPanel.add(modeCards);

        // Test dir chooser (PROJECT-LEVEL)
        testDirField = new TextFieldWithBrowseButton();
        sizeBrowse(testDirField, fieldSize);
        testDirField.addBrowseFolderListener(
                new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor())
        );
        addFormBlock(contentPanel, "Select Test Root (e.g., src/test/java):", testDirField);

        // Load state (keys/mode/model are global; test root is per-project)
        AISettings app = AISettings.getInstance();
        if ("pro".equalsIgnoreCase(app.getMode())) proRadio.setSelected(true); else freeRadio.setSelected(true);

        freeApiKeyField.setText(app.getOpenAiKey());
        proKeyField.setText(app.getProKey());

        // One-time migration from legacy global testDirectory → project-level
        String projectTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        if (projectTestDir == null || projectTestDir.isBlank()) {
            String legacy = AIProjectSettings.getInstance(project).getTestDirectory();
            String auto = legacy != null && !legacy.isBlank() ? legacy : detectTestRoot(project);
            if (auto != null && !auto.isBlank()) {
                testDirField.setText(auto);
                // don't persist here; let user hit Apply
            }
        } else {
            testDirField.setText(projectTestDir);
        }

        // Models populate
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
                + "2. Complete payment for JAIPilot Pro.<br>"
                + "3. Go to Account page.<br>"
                + "4. Copy your Account License Key from here."
                + "</div></html>");
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
        if (panel.getComponentCount() > 0) {
            panel.add(Box.createVerticalStrut(GAP_BETWEEN_BLOCKS));
        }
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
                String saved = AISettings.getInstance().getModel(); // global
                if (saved != null && allModels.contains(saved)) modelCombo.setSelectedItem(saved);
                modelCombo.setEnabled(true);
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
        return !getMode().equals(app.mode)
                || !freeApiKeyField.getText().equals(app.openAiKey)
                || !proKeyField.getText().equals(app.proKey)
                || !Objects.equals(modelCombo.getSelectedItem(), app.model)
                || !Objects.equals(testDirField.getText(), projTestDir);
    }

    @Override
    public void apply() {
        AISettings app = AISettings.getInstance();
        app.setMode(getMode());
        app.setOpenAiKey(freeApiKeyField.getText());
        app.setProKey(proKeyField.getText());
        app.setModel((String) modelCombo.getSelectedItem());

        AIProjectSettings proj = AIProjectSettings.getInstance(project);
        proj.setTestDirectory(testDirField.getText());
    }

    @Override
    public void reset() {
        AISettings.State app = AISettings.getInstance().getState();
        if ("pro".equalsIgnoreCase(app.mode)) proRadio.setSelected(true); else freeRadio.setSelected(true);
        freeApiKeyField.setText(app.openAiKey);
        proKeyField.setText(app.proKey);
        modelCombo.setSelectedItem(app.model);

        String projTestDir = AIProjectSettings.getInstance(project).getTestDirectory();
        testDirField.setText(projTestDir);
        updateModeFields();
    }

    private String getMode() { return proRadio.isSelected() ? "Pro" : "Free"; }
}
