/*
 * Copyright © 2026 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.dto;

import com.github.skrcode.javaautounittests.constants.GenerationType;
import com.github.skrcode.javaautounittests.constants.TransitionStateClass;

public class ClassGenerationMetadataStateDTO {
    private TransitionStateClass currentState;
    private String newTestSource;

    public ClassGenerationMetadataStateDTO() {
        this.currentState = null;
        this.newTestSource = null;
    }

    public TransitionStateClass getCurrentState() {
        return currentState;
    }

    public void setCurrentState(TransitionStateClass currentState) {
        this.currentState = currentState;
    }

    public String getNewTestSource() {
        return newTestSource;
    }

    public void setNewTestSource(String newTestSource) {
        this.newTestSource = newTestSource;
    }

    public boolean shouldGenerateTool(GenerationType generationType) {
        return currentState.equals(TransitionStateClass.BUILD_FAILURE) ||
               currentState.equals(TransitionStateClass.EXECUTION_FAILURE) ||
               currentState.equals(TransitionStateClass.INITIAL_BUILD_FAILURE) ||
               currentState.equals(TransitionStateClass.INITIAL_EXECUTION_FAILURE) ||
               (currentState.equals(TransitionStateClass.INITIAL_EXECUTION_SUCCESS) && generationType.equals(GenerationType.generate));
    }

    public boolean isBuildFailure() {
        return currentState.equals(TransitionStateClass.BUILD_FAILURE) ||
                currentState.equals(TransitionStateClass.INITIAL_BUILD_FAILURE);
    }

    public boolean shouldRebuild() {
        return currentState.equals(TransitionStateClass.APPLY_DONE);
    }

    public boolean isExecutionFailure() {
        return currentState.equals(TransitionStateClass.EXECUTION_FAILURE) ||
                currentState.equals(TransitionStateClass.INITIAL_EXECUTION_FAILURE);
    }

    public boolean shouldExecute() {
        return currentState.equals(TransitionStateClass.BUILD_SUCCESS) ||
                currentState.equals(TransitionStateClass.INITIAL_BUILD_SUCCESS);

    }
}


//INITIAL_BUILD_SUCCESS, - yes
//INITIAL_BUILD_FAILURE, - no
//INITIAL_EXECUTION_SUCCESS, - no
//INITIAL_EXECUTION_FAILURE, - yes
//APPLY_DONE, - no
//BUILD_SUCCESS, - yes
//BUILD_FAILURE, - no
//EXECUTION_SUCCESS, - no
//EXECUTION_FAILURE - yes