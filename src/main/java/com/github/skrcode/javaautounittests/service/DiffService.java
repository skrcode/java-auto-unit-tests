/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.service;

import com.github.skrcode.javaautounittests.view.DiffDialog;
import com.github.skrcode.javaautounittests.dto.DiffResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public final class DiffService {

    private DiffService() {}

    public static void showDiffDialog(Project project,
                                      PsiFile psiFile,
                                      DiffResult result) {
        ApplicationManager.getApplication().invokeLater(() -> {
            DiffDialog dialog = new DiffDialog(project, psiFile, result);
            dialog.show();
        });
    }
}
