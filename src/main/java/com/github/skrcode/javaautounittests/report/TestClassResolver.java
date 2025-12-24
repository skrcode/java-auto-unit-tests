package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TestClassResolver {
    private final Project project;

    public TestClassResolver(Project project) {
        this.project = project;
    }

    public String packageOf(String fqn) {
        int idx = fqn.lastIndexOf('.');
        return idx <= 0 ? "" : fqn.substring(0, idx);
    }

    /**
     * Heuristics:
     * 1) Same package + CUTTest / TestCUT / CUTIT
     * 2) Any test source class with those names (fallback)
     */
    public @Nullable PsiClass findTestClassFor(PsiClass cut) {
        String cutFqn = cut.getQualifiedName();
        if (cutFqn == null) return null;

        String pkg = packageOf(cutFqn);
        String cutSimple = cut.getName();
        if (cutSimple == null || cutSimple.isBlank()) return null;

        List<String> candidates = List.of(
                cutSimple + "Test",
                "Test" + cutSimple,
                cutSimple + "IT",
                cutSimple + "Tests"
        );

        // Prefer same package in test scope
        for (String name : candidates) {
            PsiClass c = findInTestSourcesByFqn(pkg.isEmpty() ? name : (pkg + "." + name));
            if (c != null) return c;
        }

        // Fallback: name match in any test source root
        for (String name : candidates) {
            PsiClass c = findInTestSourcesBySimpleName(name);
            if (c != null) return c;
        }

        return null;
    }

    private @Nullable PsiClass findInTestSourcesByFqn(String fqn) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope testScope = testScope();
        PsiClass c = facade.findClass(fqn, testScope);
        return (c != null && isInTestSources(c)) ? c : null;
    }

    private @Nullable PsiClass findInTestSourcesBySimpleName(String simpleName) {
        GlobalSearchScope scope = testScope();
        PsiFile[] files =
                FilenameIndex.getFilesByName(project, simpleName + ".java", scope);
        for (PsiFile f : files) {
            if (!(f instanceof PsiJavaFile)) continue;
            for (PsiClass c : ((PsiJavaFile) f).getClasses()) {
                if (simpleName.equals(c.getName()) && isInTestSources(c)) return c;
            }
        }
        return null;
    }

    private boolean isInTestSources(PsiClass c) {
        PsiFile file = c.getContainingFile();
        if (file == null) return false;
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) return false;
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        return index.isInTestSourceContent(vf);
    }

    private GlobalSearchScope testScope() {
        return GlobalSearchScope.projectScope(project); // we filter by isInTestSources anyway
    }

    public List<PsiClass> findAllClasses() {
        List<PsiClass> result = new ArrayList<>();

        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiPackage root = facade.findPackage("");

        if (root != null) {
            collectRecursively(root, result);
        }

        return result;
    }

    private void collectRecursively(PsiPackage pkg, List<PsiClass> out) {
        for (PsiClass cls : pkg.getClasses(GlobalSearchScope.projectScope(project))) {
            if (cls.getQualifiedName() != null) {
                out.add(cls);
            }
        }

        for (PsiPackage sub : pkg.getSubPackages(GlobalSearchScope.projectScope(project))) {
            collectRecursively(sub, out);
        }
    }
}
