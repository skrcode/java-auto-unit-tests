package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFinderHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class CoverageCalculator {
    private final Project project;

    public CoverageCalculator(Project project) {
        this.project = project;
    }

    public static final class CoverageResult {
        public final int totalPublicMethods;
        public final int coveredPublicMethods;
        public final List<String> uncoveredMethodSignatures;

        public CoverageResult(int totalPublicMethods, int coveredPublicMethods, List<String> uncoveredMethodSignatures) {
            this.totalPublicMethods = totalPublicMethods;
            this.coveredPublicMethods = coveredPublicMethods;
            this.uncoveredMethodSignatures = uncoveredMethodSignatures;
        }
    }

    public CoverageResult computePublicMethodCoverage(PsiClass cut, @Nullable PsiClass testClass) {
        PsiMethod[] methods = cut.getMethods();

        List<PsiMethod> publicMethods = new ArrayList<>();
        for (PsiMethod m : methods) {
            if (!m.hasModifierProperty(PsiModifier.PUBLIC)) continue;
            if (m.isConstructor()) continue;
            if (m.hasModifierProperty(PsiModifier.ABSTRACT)) continue;

            // Optional: ignore getters/setters to match “real logic coverage”
            if (PropertyUtilBase.isSimplePropertyGetter(m) || PropertyUtilBase.isSimplePropertySetter(m)) continue;

            String name = m.getName();
            if ("toString".equals(name) || "hashCode".equals(name) || "equals".equals(name)) continue;

            publicMethods.add(m);
        }

        int total = publicMethods.size();
        if (total == 0) {
            return new CoverageResult(0, 0, Collections.emptyList());
        }

        SearchScope scope = computeTestSearchScope(testClass);

        int covered = 0;
        List<String> uncovered = new ArrayList<>();

        for (PsiMethod m : publicMethods) {
            boolean hasRef = ReferencesSearch.search(m, scope).findFirst() != null;
            if (hasRef) {
                covered++;
            } else {
                uncovered.add(signatureOf(m));
            }
        }

        return new CoverageResult(total, covered, uncovered);
    }

    private SearchScope computeTestSearchScope(@Nullable PsiClass testClass) {
        if (testClass != null) {
            PsiFile f = testClass.getContainingFile();
            if (f != null) {
                return new LocalSearchScope(f);
            }
        }
        // Fallback: search in whole project (can be expensive; keep as fallback)
        return GlobalSearchScopes.projectScope(project);
    }

    private static String signatureOf(PsiMethod m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getName()).append("(");
        PsiParameterList pl = m.getParameterList();
        PsiParameter[] params = pl.getParameters();
        for (int i = 0; i < params.length; i++) {
            PsiType t = params[i].getType();
            sb.append(t.getPresentableText());
            if (i < params.length - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    /** Minimal replacement because GlobalSearchScope.projectScope() name collides here. */
    private static final class GlobalSearchScopes {
        static SearchScope projectScope(Project project) {
            return com.intellij.psi.search.GlobalSearchScope.projectScope(project);
        }
    }

    public @NotNull List<PsiClass> findTestClassesFor(@NotNull PsiClass cut) {
        List<PsiClass> out = new ArrayList<>();

        // This is the core: IntelliJ test integration
        for (PsiElement el : TestFinderHelper.findTestsForClass(cut)) {
            PsiClass cls = PsiTreeUtil.getParentOfType(el, PsiClass.class, /* strict */ false);
            if (cls != null && cls.isValid()) {
                out.add(cls);
            }
        }

        // Deterministic order for stable UI
        out.sort(Comparator.comparing(c -> c.getQualifiedName() == null ? "" : c.getQualifiedName()));
        return out;
    }
}
