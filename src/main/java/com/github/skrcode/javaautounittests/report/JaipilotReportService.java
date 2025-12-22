package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service(Service.Level.PROJECT)
public final class JaipilotReportService implements Disposable {
    private static final Logger LOG = Logger.getInstance(JaipilotReportService.class);

    private final Project project;
    private final JaipilotReportState state = new JaipilotReportState();

    private final List<Consumer<List<ClassTestReportRow>>> listeners = new CopyOnWriteArrayList<>();
    private volatile List<ClassTestReportRow> lastRows = Collections.emptyList();

    private final AtomicBoolean autoRefreshEnabled = new AtomicBoolean(true);

    public JaipilotReportService(Project project) {
        this.project = project;
    }

    public static JaipilotReportService getInstance(Project project) {
        return project.getService(JaipilotReportService.class);
    }

    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled.get();
    }

    public void setAutoRefreshEnabled(boolean enabled) {
        autoRefreshEnabled.set(enabled);
    }

    public JaipilotReportState getState() {
        return state;
    }

    public List<ClassTestReportRow> getLastRows() {
        return lastRows;
    }

    public void addListener(@NotNull Disposable parent, @NotNull Consumer<List<ClassTestReportRow>> listener) {
        listeners.add(listener);
        Disposer.register(parent, () -> listeners.remove(listener));
    }

    public void refreshAsync(@NotNull String reason) {
        if (!project.isInitialized()) return;

        // Skip while indexing; queue after smart mode.
        DumbService.getInstance(project).runWhenSmart(() -> {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "JAIPilot: Building Test Report", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(false);

                    List<ClassTestReportRow> rows = ReadAction.compute(() -> buildReport(indicator));

                    lastRows = rows;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        for (Consumer<List<ClassTestReportRow>> l : listeners) {
                            try { l.accept(rows); } catch (Throwable t) { LOG.warn(t); }
                        }
                    });
                }
            });
        });
    }

    private List<ClassTestReportRow> buildReport(@NotNull ProgressIndicator indicator) {
        JavaProjectScanner scanner = new JavaProjectScanner(project);
        TestClassResolver resolver = new TestClassResolver(project);
        CoverageCalculator coverage = new CoverageCalculator(project);

        List<PsiClass> cuts = scanner.findAllProductionClasses(indicator);
        List<ClassTestReportRow> out = new ArrayList<>(Math.max(16, cuts.size()));

        int i = 0;
        for (PsiClass cut : cuts) {
            indicator.checkCanceled();
            indicator.setFraction(cuts.isEmpty() ? 1.0 : (i / (double) cuts.size()));
            indicator.setText2("Scanning " + safeName(cut));

            PsiClass testClass = resolver.findTestClassFor(cut);

            CoverageCalculator.CoverageResult cov = coverage.computePublicMethodCoverage(cut, testClass);

            String cutFqn = Objects.toString(cut.getQualifiedName(), "");
            String cutSimple = Objects.toString(cut.getName(), "");
            String cutPkg = resolver.packageOf(cutFqn);

            String testFqn = testClass == null ? null : testClass.getQualifiedName();
            String testSimple = testClass == null ? null : testClass.getName();

            TestRunStatus status = TestRunStatus.NEVER_RUN;
            int fails = 0;
            if (testFqn != null) {
                JaipilotReportState.TestStatus s = state.get(testFqn);
                if (s != null) {
                    status = s.status;
                    fails = s.failureCount;
                }
            }

            out.add(new ClassTestReportRow(
                    cutFqn, cutSimple, cutPkg,
                    testFqn, testSimple,
                    cov.totalPublicMethods,
                    cov.coveredPublicMethods,
                    status, fails,
                    cov.uncoveredMethodSignatures,
                    cut, testClass
            ));

            i++;
        }

        // Sort: worst first (missing tests, failing, low coverage)
        out.sort(Comparator
                .comparing(ClassTestReportRow::isMissingTestClass).reversed()
                .thenComparing((ClassTestReportRow r) -> r.getLastTestStatus() == TestRunStatus.FAIL).reversed()
                .thenComparingDouble(ClassTestReportRow::coverageRatio)
                .thenComparing(ClassTestReportRow::getCutFqn)
        );

        return out;
    }

    private static String safeName(PsiClass c) {
        String q = c.getQualifiedName();
        return q != null ? q : Objects.toString(c.getName(), "<anonymous>");
    }

    @Override
    public void dispose() {}
}
