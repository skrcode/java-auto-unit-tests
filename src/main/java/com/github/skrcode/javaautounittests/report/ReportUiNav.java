package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

public final class ReportUiNav {
    private ReportUiNav() {}

    public static void openPsi(Project project, PsiElement el) {
        if (project == null || el == null) return;
        PsiFile f = el.getContainingFile();
        if (f == null) return;
        VirtualFile vf = f.getVirtualFile();
        if (vf == null) return;
        new OpenFileDescriptor(project, vf, el.getTextOffset()).navigate(true);
    }
}
