package com.github.skrcode.javaautounittests.report;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class ClassTestReportRow {
    private final @NotNull String cutFqn;
    private final @NotNull String cutSimpleName;
    private final @NotNull String cutPackageName;

    private final @Nullable String testFqn;
    private final @Nullable String testSimpleName;

    private final int totalPublicMethods;
    private final int coveredPublicMethods;

    private final @NotNull TestRunStatus lastTestStatus;
    private final int lastFailureCount;

    private final @NotNull List<String> uncoveredMethodSignatures;

    // Optionally keep these if you want navigation without re-resolve:
    private final transient @Nullable PsiClass cutPsi;
    private final transient @Nullable PsiClass testPsi;

    public ClassTestReportRow(
            @NotNull String cutFqn,
            @NotNull String cutSimpleName,
            @NotNull String cutPackageName,
            @Nullable String testFqn,
            @Nullable String testSimpleName,
            int totalPublicMethods,
            int coveredPublicMethods,
            @NotNull TestRunStatus lastTestStatus,
            int lastFailureCount,
            @NotNull List<String> uncoveredMethodSignatures,
            @Nullable PsiClass cutPsi,
            @Nullable PsiClass testPsi
    ) {
        this.cutFqn = cutFqn;
        this.cutSimpleName = cutSimpleName;
        this.cutPackageName = cutPackageName;
        this.testFqn = testFqn;
        this.testSimpleName = testSimpleName;
        this.totalPublicMethods = totalPublicMethods;
        this.coveredPublicMethods = coveredPublicMethods;
        this.lastTestStatus = lastTestStatus;
        this.lastFailureCount = lastFailureCount;
        this.uncoveredMethodSignatures = uncoveredMethodSignatures == null ? Collections.emptyList() : uncoveredMethodSignatures;
        this.cutPsi = cutPsi;
        this.testPsi = testPsi;
    }

    public @NotNull String getCutFqn() { return cutFqn; }
    public @NotNull String getCutSimpleName() { return cutSimpleName; }
    public @NotNull String getCutPackageName() { return cutPackageName; }

    public @Nullable String getTestFqn() { return testFqn; }
    public @Nullable String getTestSimpleName() { return testSimpleName; }

    public int getTotalPublicMethods() { return totalPublicMethods; }
    public int getCoveredPublicMethods() { return coveredPublicMethods; }

    public @NotNull TestRunStatus getLastTestStatus() { return lastTestStatus; }
    public int getLastFailureCount() { return lastFailureCount; }

    public @NotNull List<String> getUncoveredMethodSignatures() { return uncoveredMethodSignatures; }

    public @Nullable PsiClass getCutPsi() { return cutPsi; }
    public @Nullable PsiClass getTestPsi() { return testPsi; }

    public double coverageRatio() {
        if (totalPublicMethods <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, coveredPublicMethods / (double) totalPublicMethods));
    }

    public boolean isMissingTestClass() { return testFqn == null || testFqn.isBlank(); }
}
