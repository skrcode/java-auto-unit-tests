package com.github.skrcode.javaautounittests.report;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ClassTestReportRow(
        @NotNull String cutFqn,
        @NotNull String cutSimpleName,
        @NotNull String cutPackageName,

        @Nullable String testFqn,
        @Nullable String testSimpleName,

        int totalPublicMethods,
        int coveredPublicMethods,
        int lastFailureCount,

        @NotNull List<String> uncoveredMethodSignatures,

        @Nullable PsiClass cutPsi,
        @Nullable PsiClass testPsi
) {

    public ClassTestReportRow {
        uncoveredMethodSignatures =
                uncoveredMethodSignatures == null
                        ? List.of()
                        : List.copyOf(uncoveredMethodSignatures);
    }

    /* ---------- Derived helpers ---------- */

    public boolean isMissingTestClass() {
        return testFqn == null || testFqn.isBlank();
    }

    public boolean hasCoverageGap() {
        return totalPublicMethods > coveredPublicMethods;
    }

    public boolean hasExecutionFailures() {
        return lastFailureCount > 0;
    }

    public boolean hasFailures() {
        return isMissingTestClass() || hasCoverageGap() || hasExecutionFailures();
    }

    public double coverageRatio() {
        return totalPublicMethods <= 0
                ? 1.0
                : Math.min(1.0,
                Math.max(0.0,
                        coveredPublicMethods / (double) totalPublicMethods));
    }
}
