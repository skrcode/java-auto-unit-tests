package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AISettings", storages = @Storage("AISettings.xml"))
public class AISettings implements PersistentStateComponent<AISettings.State> {




    public static class State {
        public String openAiKey = "";       // Free API Key
        public String proKey = "";          // Pro API Key
        public String model = "";           // Gemini Model (Free mode)
//        public String testDirectory = "";   // Test directory path
        public String mode = "Free";        // Current mode: "free" or "pro"
//        public Boolean proKeyValidated = null;
        public boolean telemetryEnabled = true;
//        public boolean thinkingMode = false;
    }

    private State state = new State();

    public static AISettings getInstance() {
        return com.intellij.openapi.application.ApplicationManager
                .getApplication()
                .getService(AISettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getOpenAiKey() {
        return state.openAiKey;
    }

    public void setOpenAiKey(String key) {
        state.openAiKey = key;
    }

    public String getProKey() {
        return state.proKey;
    }

    public void setProKey(String key) {
        state.proKey = key;
    }

    public String getModel() {
        return state.model;
    }

    public void setModel(String model) {
        state.model = model;
    }

//    public String getTestDirectory() {
//        return state.testDirectory;
//    }
//
//    public void setTestDirectory(String dir) {
//        state.testDirectory = dir;
//    }

    public String getMode() {
        return state.mode;
    }

    public void setMode(String mode) {
        state.mode = mode;
    }

//    public void setProKeyValidated(Boolean isProKeyValidated) {
//        state.proKeyValidated = isProKeyValidated;
//    }
//
//    public Boolean isProKeyValidated() {
//        return state.proKeyValidated;
//    }

    public boolean isTelemetryEnabled() { return state.telemetryEnabled; }
    public void setTelemetryEnabled(boolean v) { state.telemetryEnabled = v; }

//    public boolean isThinkingMode() { return getState().thinkingMode; }
//    public void setThinkingMode(boolean v) { getState().thinkingMode = v; }

}
