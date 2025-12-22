package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class JavaProjectScanner {
    private final Project project;

    public JavaProjectScanner(Project project) {
        this.project = project;
    }

    /**
     * Finds Java classes in production source roots. This is intentionally conservative:
     * - only includes files under production source roots (not test)
     * - excludes generated / excluded content automatically
     */
    public List<PsiClass> findAllProductionClasses(@NotNull ProgressIndicator indicator) {
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        Collection<VirtualFile> javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope);

        PsiManager psiManager = PsiManager.getInstance(project);
        List<PsiClass> out = new ArrayList<>(Math.max(64, javaFiles.size()));

        int i = 0;
        for (VirtualFile vf : javaFiles) {
            indicator.checkCanceled();
            i++;

            // Only production source content
            if (!index.isInSourceContent(vf)) continue;
            if (index.isInTestSourceContent(vf)) continue;

            PsiFile pf = psiManager.findFile(vf);
            if (!(pf instanceof PsiJavaFile)) continue;

            PsiJavaFile jf = (PsiJavaFile) pf;
            for (PsiClass c : jf.getClasses()) {
                if (c.getQualifiedName() == null) continue;
                if (c.isInterface() || c.isEnum() || c.isAnnotationType()) continue;
                out.add(c);
            }
        }
        return out;
    }
}
