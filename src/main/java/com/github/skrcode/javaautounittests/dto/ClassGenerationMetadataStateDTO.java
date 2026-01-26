/*
 * Copyright © 2026 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.dto;

public class ClassGenerationMetadataStateDTO {
    private boolean shouldRebuild;
    private boolean isLLMGeneratedAtleastOnce;
    private boolean isPlanGenerated;
    private String newTestSource;
    private boolean readyToGeneratePlan;
    private boolean buildAndRunSuccess;
    private boolean buildSuccess;

    public ClassGenerationMetadataStateDTO() {
        this.shouldRebuild = true;
        this.isLLMGeneratedAtleastOnce = false;
        this.isPlanGenerated = false;
        this.newTestSource = null;
        this.readyToGeneratePlan = false;
        this.buildAndRunSuccess = false;
        this.buildSuccess = false;
    }

    public boolean isShouldRebuild() {
        return shouldRebuild;
    }

    public void setShouldRebuild(boolean shouldRebuild) {
        this.shouldRebuild = shouldRebuild;
    }

    public boolean isLLMGeneratedAtleastOnce() {
        return isLLMGeneratedAtleastOnce;
    }

    public void setLLMGeneratedAtleastOnce(boolean LLMGeneratedAtleastOnce) {
        isLLMGeneratedAtleastOnce = LLMGeneratedAtleastOnce;
    }

    public boolean isPlanGenerated() {
        return isPlanGenerated;
    }

    public void setPlanGenerated(boolean planGenerated) {
        isPlanGenerated = planGenerated;
    }

    public String getNewTestSource() {
        return newTestSource;
    }

    public void setNewTestSource(String newTestSource) {
        this.newTestSource = newTestSource;
    }

    public boolean isReadyToGeneratePlan() {
        return readyToGeneratePlan;
    }

    public void setReadyToGeneratePlan(boolean readyToGeneratePlan) {
        this.readyToGeneratePlan = readyToGeneratePlan;
    }

    public boolean isBuildAndRunSuccess() {
        return buildAndRunSuccess;
    }

    public void setBuildAndRunSuccess(boolean buildAndRunSuccess) {
        this.buildAndRunSuccess = buildAndRunSuccess;
    }

    public boolean isBuildSuccess() {
        return buildSuccess;
    }

    public void setBuildSuccess(boolean buildSuccess) {
        this.buildSuccess = buildSuccess;
    }
}
