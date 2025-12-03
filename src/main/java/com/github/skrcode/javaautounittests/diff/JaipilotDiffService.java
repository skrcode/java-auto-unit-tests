/*
 * Copyright © 2025 Suraj Rajan / JAIPilot
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.github.skrcode.javaautounittests.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public final class JaipilotDiffService {

    private JaipilotDiffService() {}

    public static void showDiffDialog(Project project,
                                      PsiFile psiFile,
                                      JaipilotDiffResult result) {
        ApplicationManager.getApplication().invokeLater(() -> {
            JaipilotDiffDialog dialog = new JaipilotDiffDialog(project, psiFile, result);
            dialog.show();
        });
    }
}
