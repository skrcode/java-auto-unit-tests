package com.github.skrcode.javaautounittests.dto;

import com.github.skrcode.javaautounittests.util.CoverageCalculator;
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
        @Nullable PsiClass testPsi,
        @NotNull CoverageStatus coverageStatus
) {

    public enum CoverageStatus {
        NOT_ANALYZED,
        ANALYZED
    }

    public ClassTestReportRow {
        uncoveredMethodSignatures =
                uncoveredMethodSignatures == null
                        ? List.of()
                        : List.copyOf(uncoveredMethodSignatures);
        coverageStatus = coverageStatus == null ? CoverageStatus.NOT_ANALYZED : coverageStatus;
    }

    /* ---------- Derived helpers ---------- */

    public boolean isMissingTestClass() {
        return testFqn == null || testFqn.isBlank();
    }

    public boolean hasCoverageGap() {
        if (coverageStatus != CoverageStatus.ANALYZED) return false;
        return totalPublicMethods > coveredPublicMethods;
    }

    public boolean hasExecutionFailures() {
        return lastFailureCount > 0;
    }

    public boolean hasFailures() {
        return isMissingTestClass() || hasCoverageGap() || hasExecutionFailures();
    }

    public double coverageRatio() {
        if (coverageStatus != CoverageStatus.ANALYZED) return 0.0;
        return totalPublicMethods <= 0
                ? 1.0
                : Math.min(1.0,
                Math.max(0.0,
                        coveredPublicMethods / (double) totalPublicMethods));
    }

    public @NotNull ClassTestReportRow withCoverage(@NotNull CoverageCalculator.CoverageResult result) {
        return withCoverage(result.totalPublicMethods, result.coveredPublicMethods, result.uncoveredMethodSignatures);
    }

    public @NotNull ClassTestReportRow withCoverage(
            int totalMethods,
            int coveredMethods,
            @Nullable List<String> uncoveredSignatures
    ) {
        List<String> safeUncovered = uncoveredSignatures == null ? List.of() : List.copyOf(uncoveredSignatures);
        if (coverageStatus == CoverageStatus.ANALYZED
                && totalPublicMethods == totalMethods
                && coveredPublicMethods == coveredMethods
                && uncoveredMethodSignatures.equals(safeUncovered)) {
            return this;
        }
        return new ClassTestReportRow(
                cutFqn,
                cutSimpleName,
                cutPackageName,
                testFqn,
                testSimpleName,
                totalMethods,
                coveredMethods,
                lastFailureCount,
                safeUncovered,
                cutPsi,
                testPsi,
                CoverageStatus.ANALYZED
        );
    }

    public @NotNull ClassTestReportRow withFailureCount(int failures) {
        int safeFailures = Math.max(0, failures);
        if (lastFailureCount == safeFailures) return this;
        return new ClassTestReportRow(
                cutFqn,
                cutSimpleName,
                cutPackageName,
                testFqn,
                testSimpleName,
                totalPublicMethods,
                coveredPublicMethods,
                safeFailures,
                uncoveredMethodSignatures,
                cutPsi,
                testPsi,
                coverageStatus
        );
    }
}
