// Copyright © 2026 Suraj Rajan / JAIPilot
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, you can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.skrcode.javaautounittests.actions;

import com.github.skrcode.javaautounittests.constants.GenerationType;
import com.github.skrcode.javaautounittests.service.BulkGeneratorService;
import com.github.skrcode.javaautounittests.state.AISettings;
import com.github.skrcode.javaautounittests.util.Telemetry;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Entry‑point action – collects one or many classes/directories and delegates to the worker service.
 */
public class GenerateTestAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PsiElement[] elements = ReadAction.compute(() -> getElements(e, project));
        if (elements == null) return;

        List<PsiClass> classes = ReadAction.compute(() -> collectClasses(elements));
        GenerationType generationType = getGenerationType(e);
        if (generationType == null) return;
        runForClasses(project, classes, generationType);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Presentation presentation = e.getPresentation();
        if (project == null) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        boolean visible;
        try {
            visible = ReadAction.compute(() -> computeVisibility(e, project));
        } catch (ProcessCanceledException ignored) {
            visible = false;
        }
        presentation.setEnabledAndVisible(visible);
    }

    private boolean computeVisibility(@NotNull AnActionEvent e, @NotNull Project project) {
        if (project.isDisposed()) return false;

        PsiElement[] elements = getElements(e, project);
        if (elements == null) return false;

        List<PsiClass> classes = collectClasses(elements);
        if (classes.isEmpty()) return false;

        GenerationType generationType = getGenerationType(e);
        if (generationType == null) return false;

        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        boolean allInTests = true;
        boolean allInProduction = true;

        for (PsiClass psiClass : classes) {
            PsiFile containingFile = psiClass.getContainingFile();
            VirtualFile virtualFile = containingFile != null ? containingFile.getVirtualFile() : null;
            if (virtualFile == null) return false;
            boolean isTest = fileIndex.isInTestSourceContent(virtualFile);
            allInTests &= isTest;
            allInProduction &= !isTest;
        }

        return switch (generationType) {
            case generate -> allInProduction;
            case fix -> allInTests;
        };
    }

    private GenerationType getGenerationType(@NotNull AnActionEvent e) {
        String actionId = e.getActionManager().getId(this);
        if (actionId == null) return null;
        try {
            return GenerationType.valueOf(actionId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean runForClasses(Project project, List<PsiClass> classes, GenerationType generationType) {
        if (project == null) return false;
        if (classes == null || classes.isEmpty()) {
            Telemetry.uiSettingsFailureClick("classes empty");
            Messages.showErrorDialog(project, "No Java classes found in selection.", "JAIPilot");
            return false;
        }
        if (classes.size() > 50) {
            Telemetry.uiSettingsFailureClick("more than max classes selected");
            Messages.showErrorDialog(project, "Please select fewer than 50 java classes.", "JAIPilot");
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
//        if (AIProjectSettings.getInstance(project).getTestDirectory().isEmpty()) {
//            Telemetry.uiSettingsFailureClick("test directory not configured in settings");
//            Messages.showErrorDialog(project, "Please configure test directory in settings.", "JAIPilot");
//            return false;
//        }
        BulkGeneratorService.enqueue(project, classes, generationType);
        return true;
    }

    private PsiElement[] getElements(@NotNull AnActionEvent e, @NotNull Project project) {
        PsiElement[] elements = e.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        if (elements != null) return elements;
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (psiElement != null) return new PsiElement[]{psiElement};
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile != null) return new PsiElement[]{psiFile};

        VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (virtualFiles != null && virtualFiles.length > 0) {
            PsiManager psiManager = PsiManager.getInstance(project);
            List<PsiElement> collected = new ArrayList<>(virtualFiles.length);
            for (VirtualFile vf : virtualFiles) {
                if (vf.isDirectory()) {
                    PsiDirectory psiDirectory = psiManager.findDirectory(vf);
                    if (psiDirectory != null) collected.add(psiDirectory);
                } else {
                    PsiFile vfPsiFile = psiManager.findFile(vf);
                    if (vfPsiFile != null) collected.add(vfPsiFile);
                }
            }
            if (!collected.isEmpty()) return collected.toArray(PsiElement[]::new);
        }
        return null;
    }


    private List<PsiClass> collectClasses(PsiElement[] elements) {
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
            return ReadAction.compute(() -> collectClasses(elements));
        }

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
                        // Do not descend into inner classes; only collect top‑level declarations.
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
