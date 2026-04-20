package com.github.skrcode.javaautounittests.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFinderHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class CoverageCalculator {
    public CoverageCalculator() {}

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
        if (testClass == null) {
            return computePublicMethodCoverage(cut, Collections.emptyList());
        }
        return computePublicMethodCoverage(cut, List.of(testClass));
    }

    public int countCoverablePublicMethods(PsiClass cut) {
        return collectCoverablePublicMethods(cut).size();
    }

    public CoverageResult computePublicMethodCoverage(PsiClass cut, @NotNull List<PsiClass> testClasses) {
        List<PsiMethod> publicMethods = collectCoverablePublicMethods(cut);
        int total = publicMethods.size();
        if (total == 0) {
            return new CoverageResult(0, 0, Collections.emptyList());
        }

        // If no matching test class is found, coverage is zero by definition.
        if (testClasses.isEmpty()) {
            return zeroCoverage(publicMethods);
        }

        Set<String> candidateSignatures = new HashSet<>(total);
        for (PsiMethod method : publicMethods) {
            candidateSignatures.add(signatureOf(method));
        }
        Set<String> coveredSignatures = collectCoveredMethodSignatures(cut, testClasses, candidateSignatures);
        List<String> uncovered = new ArrayList<>();

        for (PsiMethod m : publicMethods) {
            if (!coveredSignatures.contains(signatureOf(m))) {
                uncovered.add(signatureOf(m));
            }
        }

        int covered = coveredSignatures.size();
        return new CoverageResult(total, covered, uncovered);
    }

    private static List<PsiMethod> collectCoverablePublicMethods(PsiClass cut) {
        PsiMethod[] methods = cut.getMethods();
        List<PsiMethod> publicMethods = new ArrayList<>(methods.length);
        for (PsiMethod m : methods) {
            if (!m.hasModifierProperty(PsiModifier.PUBLIC)) continue;
            if (m.isConstructor()) continue;

            publicMethods.add(m);
        }
        return publicMethods;
    }

    private static CoverageResult zeroCoverage(List<PsiMethod> methods) {
        List<String> uncovered = new ArrayList<>(methods.size());
        for (PsiMethod m : methods) {
            uncovered.add(signatureOf(m));
        }
        return new CoverageResult(methods.size(), 0, uncovered);
    }

    private static @NotNull Set<String> collectCoveredMethodSignatures(
            @NotNull PsiClass cut,
            @NotNull List<PsiClass> testClasses,
            @NotNull Set<String> candidateSignatures
    ) {
        Set<String> covered = new HashSet<>();
        for (PsiClass testClass : testClasses) {
            if (testClass == null || !testClass.isValid()) continue;
            PsiFile file = testClass.getContainingFile();
            if (file == null) continue;

            for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(file, PsiMethodCallExpression.class)) {
                PsiMethod resolved = call.resolveMethod();
                if (!isMethodBelongingToCutHierarchy(cut, resolved)) continue;
                String sig = signatureOf(resolved);
                if (candidateSignatures.contains(sig)) {
                    covered.add(sig);
                }
            }
            for (PsiMethodReferenceExpression methodRef : PsiTreeUtil.findChildrenOfType(file, PsiMethodReferenceExpression.class)) {
                PsiElement resolved = methodRef.resolve();
                if (!(resolved instanceof PsiMethod resolvedMethod)) continue;
                if (!isMethodBelongingToCutHierarchy(cut, resolvedMethod)) continue;
                String sig = signatureOf(resolvedMethod);
                if (candidateSignatures.contains(sig)) {
                    covered.add(sig);
                }
            }
            if (covered.size() == candidateSignatures.size()) {
                break;
            }
        }
        return covered;
    }

    private static boolean isMethodBelongingToCutHierarchy(@NotNull PsiClass cut, @Nullable PsiMethod method) {
        if (method == null) return false;
        PsiClass owner = method.getContainingClass();
        if (owner == null) return false;
        return owner.isEquivalentTo(cut) || cut.isInheritor(owner, true);
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

    public @NotNull List<PsiClass> findTestClassesFor(@NotNull PsiClass cut) {
        List<PsiClass> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // This is the core: IntelliJ test integration
        for (PsiElement el : TestFinderHelper.findTestsForClass(cut)) {
            PsiClass cls = PsiTreeUtil.getParentOfType(el, PsiClass.class, /* strict */ false);
            if (cls != null && cls.isValid()) {
                String key = cls.getQualifiedName();
                if (key == null || key.isBlank()) {
                    PsiFile f = cls.getContainingFile();
                    key = f == null ? cls.getName() : f.getVirtualFile() == null ? cls.getName() : f.getVirtualFile().getPath();
                }
                if (key == null || !seen.add(key)) continue;
                out.add(cls);
            }
        }

        // Deterministic order for stable UI
        out.sort(Comparator.comparing(c -> c.getQualifiedName() == null ? "" : c.getQualifiedName()));
        return out;
    }
}
