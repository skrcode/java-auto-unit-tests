package com.github.skrcode.javaautounittests;

import com.github.skrcode.javaautounittests.settings.AISettings;
import com.github.skrcode.javaautounittests.settings.AISettingsDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
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
        if (classes.isEmpty()) {
            Messages.showErrorDialog(project, "No Java classes found in selection.", "JAIPilot");
            return;
        }
        if (classes.size() > 1) {
            Messages.showErrorDialog(project, "Please select only single java class.", "JAIPilot");
            return;
        }
        if (AISettings.getInstance().getModel().isEmpty()|| AISettings.getInstance().getTestDirectory().isEmpty() || AISettings.getInstance().getOpenAiKey().isEmpty()) {
            Messages.showErrorDialog(project, "Please configure details in settings.", "JAIPilot");
            return;
        }
        // Show settings dialog
        AISettingsDialog dialog = new AISettingsDialog();
        boolean okPressed = dialog.showAndGet(); // returns true if OK, false if Cancel

        if (!okPressed) return;
        BulkGeneratorService.enqueue(project, classes, stringPathToPsiDirectory(project,AISettings.getInstance().getTestDirectory()));
    }

    private static @Nullable PsiDirectory stringPathToPsiDirectory(Project project, String path) {
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
        if (file == null || !file.isDirectory()) {
            return null;
        }
        return PsiManager.getInstance(project).findDirectory(file);
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