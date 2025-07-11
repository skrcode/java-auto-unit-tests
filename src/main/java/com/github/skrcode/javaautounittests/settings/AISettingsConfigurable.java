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
import java.util.Map;

public class AISettingsConfigurable implements Configurable {

    private JPasswordField apiKeyField;
    private Component modelField;
    private JComboBox<String> modelCombo;
    private JPanel panel;
    private TextFieldWithBrowseButton testDirField;


    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "JAIPilot - AI Unit Test Generator";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // API Key input field
        apiKeyField = new JBPasswordField();
        apiKeyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        apiKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        // Create model combo with placeholder first
        modelCombo = new JComboBox<>(new String[]{"Loading models..."});
        modelCombo.setEnabled(false);
        modelCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        // Test Directory Picker
        testDirField = new TextFieldWithBrowseButton();
        testDirField.setAlignmentX(Component.LEFT_ALIGNMENT);
        testDirField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        testDirField.addBrowseFolderListener(
                new TextBrowseFolderListener(
                        FileChooserDescriptorFactory.createSingleFolderDescriptor()
                )
        );

        // Add components to panel
        panel.add(Box.createVerticalStrut(8));
        panel.add(new JLabel("Gemini Model API Key:"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(apiKeyField);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("Select Gemini Model:"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(modelCombo);
        panel.add(Box.createVerticalStrut(12));
        panel.add(new JLabel("Select Test Root (e.g., src/test/java):"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(testDirField);
        panel.add(Box.createVerticalGlue());

        // Load model list in background
        new Thread(this::loadModelsInBackground).start();

        return panel;
    }

    private void loadModelsInBackground() {
        try {
            Map<String, java.util.List<String>> models = PromptBuilder.getModels(); // your working method
            java.util.List<String> allModels = models.values().stream().flatMap(java.util.List::stream).toList();

            SwingUtilities.invokeLater(() -> {
                modelCombo.removeAllItems();
                for (String model : allModels) {
                    modelCombo.addItem(model);
                }
                modelCombo.setEnabled(true);

                // Restore previously selected model if any
                String savedModel = AISettings.getInstance().getState().model;
                if (savedModel != null && allModels.contains(savedModel)) {
                    modelCombo.setSelectedItem(savedModel);
                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                modelCombo.removeAllItems();
                modelCombo.addItem("Error loading models");
                modelCombo.setEnabled(false);
            });
        }
    }




    @Override
    public boolean isModified() {
        AISettings.State settings = AISettings.getInstance().getState();
        return !apiKeyField.getText().equals(settings.openAiKey)
                || !modelCombo.getSelectedItem().equals(settings.model)
                || !testDirField.getText().equals(settings.testDirectory);
    }

    @Override
    public void apply() {
        AISettings.getInstance().setOpenAiKey(apiKeyField.getText());
        AISettings.getInstance().setModel((String) modelCombo.getSelectedItem());
        AISettings.getInstance().setTestDirectory(testDirField.getText());

    }

    @Override
    public void reset() {
        AISettings.State settings = AISettings.getInstance().getState();
        apiKeyField.setText(settings.openAiKey);
        modelCombo.setSelectedItem(settings.model);
        testDirField.setText(settings.testDirectory);

    }
}
