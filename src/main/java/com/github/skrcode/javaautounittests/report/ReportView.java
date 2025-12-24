package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ReportView implements Disposable {

    private final Project project;
    private final ReportState state = new ReportState();

    private final List<Consumer<List<ClassTestReportRow>>> listeners =
            new CopyOnWriteArrayList<>();

    private volatile List<ClassTestReportRow> lastRows = Collections.emptyList();
    private final AtomicBoolean autoRefreshEnabled = new AtomicBoolean(true);

    public static ReportView getInstance(Project project) {
        return project.getService(ReportView.class);
    }

    public ReportView(Project project) {
        this.project = project;
        buildUI();
        hookListeners();
        addListener(this, this::onRowsUpdated);

        // Initial refresh so the table is populated when opened
        ApplicationManager.getApplication().invokeLater(() -> refreshAsync("init"));
    }

    public ReportState getState() {
        return state;
    }

    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled.get();
    }

    public void setAutoRefreshEnabled(boolean enabled) {
        autoRefreshEnabled.set(enabled);
    }

    public List<ClassTestReportRow> getLastRows() {
        return lastRows;
    }

    public List<ClassTestReportRow> getSelectedRows() {
        return tableModel.getRowsAt(table.getSelectedRows());
    }

    public void addListener(@NotNull Disposable parent,
                            @NotNull Consumer<List<ClassTestReportRow>> listener) {
        listeners.add(listener);
        Disposer.register(parent, () -> listeners.remove(listener));
    }

    /* =======================
       Background refresh
       ======================= */

    public void refreshAsync(@NotNull String reason) {
        if (!project.isInitialized()) return;

        DumbService.getInstance(project).runWhenSmart(() ->
                ProgressManager.getInstance().run(
                        new Task.Backgroundable(project,
                                "JAIPilot: Building Test Report", false) {

                            @Override
                            public void run(@NotNull ProgressIndicator indicator) {
                                indicator.setIndeterminate(false);

                                List<ClassTestReportRow> rows =
                                        ReadAction.compute(() -> buildReport(indicator));

                                lastRows = rows;

                                ApplicationManager.getApplication().invokeLater(() -> {
                                    for (Consumer<List<ClassTestReportRow>> l : listeners) {
                                        try {
                                            l.accept(rows);
                                        } catch (Throwable ignored) {}
                                    }
                                });
                            }
                        })
        );
    }

    private List<ClassTestReportRow> buildReport(@NotNull ProgressIndicator indicator) {
        TestClassResolver resolver = new TestClassResolver(project);
        CoverageCalculator coverage = new CoverageCalculator(project);

        List<PsiClass> cuts = resolver.findAllClasses();
        List<ClassTestReportRow> out = new ArrayList<>(cuts.size());

        int i = 0;
        for (PsiClass cut : cuts) {
            indicator.checkCanceled();
            indicator.setFraction(cuts.isEmpty() ? 1.0 : i / (double) cuts.size());
            indicator.setText2("Scanning " + safeName(cut));

            String fqn = Objects.toString(cut.getQualifiedName(), "");
            String simple = Objects.toString(cut.getName(), "");
            String pkg = resolver.packageOf(fqn);

            PsiClass testClass = resolver.findTestClassFor(cut);
            CoverageCalculator.CoverageResult cov = coverage.computePublicMethodCoverage(cut, testClass);
            if (cov.totalPublicMethods <= 0) {
                i++;
                continue; // Skip classes with no public methods
            }

            String testFqn = testClass == null ? null : testClass.getQualifiedName();
            String testSimple = testClass == null ? null : testClass.getName();

            int fails = 0;
            if (testFqn != null) {
                var st = state.get(testFqn);
                if (st != null) {
                    fails = st.failureCount;
                }
            }

            out.add(new ClassTestReportRow(
                    fqn,
                    simple,
                    pkg,
                    testFqn,
                    testSimple,
                    cov.totalPublicMethods,
                    cov.coveredPublicMethods,
                    fails,
                    cov.uncoveredMethodSignatures,
                    cut,
                    testClass
            ));
            i++;
        }

        out.sort(Comparator
                .comparing(ClassTestReportRow::isMissingTestClass).reversed()
                .thenComparing((ClassTestReportRow r) -> r.lastFailureCount() > 0).reversed()
                .thenComparingDouble(ClassTestReportRow::coverageRatio)
                .thenComparing(ClassTestReportRow::cutFqn)
        );

        return out;
    }

    private static String safeName(PsiClass c) {
        return c.getQualifiedName() != null
                ? c.getQualifiedName()
                : Objects.toString(c.getName(), "<anonymous>");
    }

    /* =======================
       UI
       ======================= */

    private final JPanel root = new JPanel(new BorderLayout());
    private final ReportTableModel tableModel = new ReportTableModel();
    private final JBTable table = new JBTable(tableModel);

    public JComponent getComponent() {
        return root;
    }

    private void buildUI() {
        root.setBorder(JBUI.Borders.empty());

        table.setRowHeight(JBUI.scale(28));
        table.setShowGrid(false);
        table.setStriped(true);
        ReportTableRenderers.install(table, project);

        root.add(new JBScrollPane(table), BorderLayout.CENTER);
    }

    private void hookListeners() {
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent event) {
                ClassTestReportRow row = tableModel.getRowAt(table.getSelectedRow());
                if (row != null && row.cutPsi() != null) {
                    openPsi(row.cutPsi());
                }
                return true;
            }
        }.installOn(table);
    }

    private void onRowsUpdated(List<ClassTestReportRow> rows) {
        tableModel.setRows(rows);
        updateSummary(tableModel.getFilteredRows());
    }

    private void updateSummary(List<ClassTestReportRow> rows) {}

    private void openPsi(PsiElement el) {
        PsiFile f = el.getContainingFile();
        if (f == null) return;
        VirtualFile vf = f.getVirtualFile();
        if (vf == null) return;
        new OpenFileDescriptor(project, vf, el.getTextOffset()).navigate(true);
    }

    @Override
    public void dispose() {}
}
