/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.state;

import com.github.skrcode.javaautounittests.dto.CacheDTO;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(
    name = "LLMGetFilesCache",
    storages = @Storage("jaipilot-llm-get-files-cache.xml")
)
@Service(Service.Level.PROJECT)
public final class GenerateTestsGetFilesCache
        implements PersistentStateComponent<GenerateTestsGetFilesCache.State> {

    public static final class State {
        /** Key = fully qualified class name */
        public Map<String, CacheDTO> cacheByClassFqn = new HashMap<>();
    }

    private State state = new State();

    public static GenerateTestsGetFilesCache getInstance(@NotNull Project project) {
        return project.getService(GenerateTestsGetFilesCache.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    @Nullable
    public CacheDTO get(@NotNull String classFqn) {
        return state.cacheByClassFqn.get(classFqn);
    }

    public void put(@NotNull String classFqn, @NotNull CacheDTO dto) {
        dto.touch();
        state.cacheByClassFqn.put(classFqn, dto);
    }

    public void invalidate(@NotNull String classFqn) {
        state.cacheByClassFqn.remove(classFqn);
    }

    public void clearAll() {
        state.cacheByClassFqn.clear();
    }

    public boolean contains(@NotNull String classFqn) {
        return state.cacheByClassFqn.containsKey(classFqn);
    }
}
