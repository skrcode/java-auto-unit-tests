package com.github.skrcode.javaautounittests.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AIProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class AIProjectSettings implements PersistentStateComponent<AIProjectSettings.State> {

    public static final class State {
        public String testDirectory = "";
    }

    private State state = new State();

    public static AIProjectSettings getInstance(@NotNull Project project) {
        return project.getService(AIProjectSettings.class);
    }

    @Nullable @Override
    public State getState() { return state; }

    @Override
    public void loadState(@NotNull State state) { this.state = state; }

    public String getTestDirectory() { return state.testDirectory; }

    public void setTestDirectory(String dir) { state.testDirectory = dir; }
}
