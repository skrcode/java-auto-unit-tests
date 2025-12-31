// Copyright © 2025 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.actions;

import com.github.skrcode.javaautounittests.GenerationType;
import com.github.skrcode.javaautounittests.service.BulkGeneratorService;
import com.github.skrcode.javaautounittests.state.AISettings;
import com.github.skrcode.javaautounittests.util.Telemetry;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Entry‑point action – collects one or many classes/directories and delegates to the worker service.
 */
public class GenerateTestAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);

        if (project == null || elements == null) return;

        List<PsiClass> classes = collectClasses(elements);
        String actionId = e.getActionManager().getId(this);
        runForClasses(project, classes, GenerationType.valueOf(actionId));
    }

    private static @Nullable PsiDirectory stringPathToPsiDirectory(Project project, String path) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null || !file.isDirectory()) {
            return null;
        }
        return PsiManager.getInstance(project).findDirectory(file);
    }

    private static boolean runForClasses(Project project, List<PsiClass> classes, GenerationType actionId) {
        if (project == null) return false;

        if (classes == null || classes.isEmpty()) {
            Telemetry.uiSettingsFailureClick("classes empty");
            Messages.showErrorDialog(project, "No Java classes found in selection.", "JAIPilot");
            return false;
        }
        if (classes.size() > 1) {
            Telemetry.uiSettingsFailureClick("multiple classes selected");
            Messages.showErrorDialog(project, "Please select only single java class.", "JAIPilot");
            return false;
        }
        if (AISettings.getInstance().getProKey().isEmpty()) {
            Telemetry.uiSettingsFailureClick("license key not configured in settings");
            Messages.showErrorDialog(project, "Please configure license key in settings.", "JAIPilot");
            ApplicationManager.getApplication().invokeLater(() -> {
                ShowSettingsUtil.getInstance()
                        .showSettingsDialog(
                                project,
                                "JAIPilot - One-Click AI Agent for Java Unit Testing"
                        );
            });
            return false;
        }
//        if (validateLicesekey(AISettings.getInstance().getProKey())) {
//            Telemetry.uiSettingsFailureClick("license key not valid.");
//            Messages.showErrorDialog(project, "Invalid License Key configured", "JAIPilot");
//            ApplicationManager.getApplication().invokeLater(() -> {
//                ShowSettingsUtil.getInstance()
//                        .showSettingsDialog(
//                                project,
//                                "JAIPilot - One-Click AI Agent for Java Unit Testing"
//                        );
//            });
//            return false;
//        }
//        if (AIProjectSettings.getInstance(project).getTestDirectory().isEmpty()) {
//            Telemetry.uiSettingsFailureClick("test directory not configured in settings");
//            Messages.showErrorDialog(project, "Please configure test directory in settings.", "JAIPilot");
//            return false;
//        }
        // Make build compiler checks before proceeding to test generation.

        BulkGeneratorService.enqueue(
                project,
                classes.get(0),actionId
        );
        return true;
    }


    private List<PsiClass> collectClasses(PsiElement[] elements) {
        List<PsiClass> classes = new ArrayList<>();

        for (PsiElement element : elements) {

            if (element instanceof PsiClass psiClass) {
                classes.add(psiClass);

            } else if (element instanceof PsiJavaFile javaFile) {
                classes.addAll(List.of(javaFile.getClasses()));

            } else if (element instanceof PsiDirectory dir) {
                // ✅ Recursively collect from directory
                dir.accept(new JavaRecursiveElementVisitor() {
                    @Override public void visitClass(PsiClass aClass) {
                        classes.add(aClass);
                        super.visitClass(aClass);
                    }
                });

            } else if (element instanceof PsiPackage pkg) {
                // ✅ Add classes in package, but not recursively
                Collections.addAll(classes, pkg.getClasses());
            }
        }

        return classes;
    }

}
