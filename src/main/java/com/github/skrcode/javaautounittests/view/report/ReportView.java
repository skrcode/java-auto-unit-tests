package com.github.skrcode.javaautounittests.view.report;

import com.github.skrcode.javaautounittests.dto.ClassTestReportRow;
import com.github.skrcode.javaautounittests.service.ReportState;
import com.github.skrcode.javaautounittests.util.CoverageCalculator;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ReportView implements Disposable {

    private static final int COVERAGE_UI_BATCH_SIZE = 20;
    private static final Comparator<ClassTestReportRow> ROW_COMPARATOR = Comparator
            .comparing(ClassTestReportRow::isMissingTestClass).reversed()
            .thenComparing((ClassTestReportRow r) -> r.lastFailureCount() > 0).reversed()
            .thenComparing(ClassTestReportRow::cutFqn)
            .thenComparing(r -> Objects.toString(r.testFqn(), ""));

    private final Project project;
    private final ReportState state = new ReportState();

    private final List<Consumer<List<ClassTestReportRow>>> listeners =
            new CopyOnWriteArrayList<>();
    private final Object rowsLock = new Object();

    private volatile List<ClassTestReportRow> lastRows = Collections.emptyList();
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);
    private final AtomicBoolean coverageInProgress = new AtomicBoolean(false);
    private final AtomicReference<ProgressIndicator> coverageIndicatorRef = new AtomicReference<>();
    private final Map<String, CoverageCalculator.CoverageResult> coverageCache = new ConcurrentHashMap<>();

    public static ReportView getInstance(Project project) {
        return project.getService(ReportView.class);
    }

    public ReportView(Project project) {
        this.project = project;
        buildUI();
        hookListeners();
        addListener(this, this::onRowsUpdated);
        updateActionButtons();
    }

    public ReportState getState() {
        return state;
    }

    public List<ClassTestReportRow> getLastRows() {
        return lastRows;
    }

    public List<ClassTestReportRow> getSelectedRows() {
        return tableModel.getRowsAt(table.getSelectedRows());
    }

    public void addListener(
            @NotNull Disposable parent,
            @NotNull Consumer<List<ClassTestReportRow>> listener
    ) {
        listeners.add(listener);
        Disposer.register(parent, () -> listeners.remove(listener));
    }

    public void refreshAsync(@NotNull String reason) {
        if (!project.isInitialized()) return;

        cancelCoverageAnalysis();
        coverageCache.clear();

        if (!refreshInProgress.compareAndSet(false, true)) {
            refreshPending.set(true);
            return;
        }
        updateActionButtonsAsync();

        DumbService.getInstance(project).runWhenSmart(() ->
                ProgressManager.getInstance().run(new Task.Backgroundable(
                        project,
                        "JAIPilot: Building Test Report",
                        true
                ) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(false);
                        List<ClassTestReportRow> rows;
                        try {
                            rows = buildStructuralReport(indicator);
                        } catch (ProcessCanceledException canceled) {
                            return;
                        } catch (Throwable ignored) {
                            rows = Collections.emptyList();
                        }

                        updateRows(rows, false);
                        List<ClassTestReportRow> snapshot = lastRows;
                        ApplicationManager.getApplication().invokeLater(() -> {
                            publishRowsOnEdt(snapshot);
                            markFresh();
                        });
                    }

                    @Override
                    public void onFinished() {
                        refreshInProgress.set(false);
                        updateActionButtonsAsync();
                        if (refreshPending.getAndSet(false)) {
                            refreshAsync("coalesced");
                        }
                    }
                })
        );
    }

    public void analyzeCoverageAsync() {
        if (!project.isInitialized()) return;
        if (refreshInProgress.get()) return;
        if (!coverageInProgress.compareAndSet(false, true)) return;

        List<CutCoverageWorkItem> work = snapshotCoverageWork();
        if (work.isEmpty()) {
            coverageInProgress.set(false);
            updateActionButtonsAsync();
            return;
        }
        updateActionButtonsAsync();

        DumbService.getInstance(project).runWhenSmart(() ->
                ProgressManager.getInstance().run(new Task.Backgroundable(
                        project,
                        "JAIPilot: Analyzing Coverage",
                        true
                ) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(false);
                        coverageIndicatorRef.set(indicator);

                        CoverageCalculator coverage = new CoverageCalculator();
                        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
                        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

                        Map<String, CoverageCalculator.CoverageResult> batch = new HashMap<>();
                        int i = 0;
                        for (CutCoverageWorkItem item : work) {
                            indicator.checkCanceled();
                            indicator.setFraction(work.isEmpty() ? 1.0 : i / (double) work.size());
                            indicator.setText2("Analyzing " + item.cutFqn);

                            CoverageCalculator.CoverageResult result = ReadAction.compute(
                                    () -> computeCoverageForCut(item, coverage, psiFacade, scope)
                            );
                            if (result != null) {
                                batch.put(item.cutFqn, result);
                            }

                            i++;
                            if (batch.size() >= COVERAGE_UI_BATCH_SIZE || i == work.size()) {
                                Map<String, CoverageCalculator.CoverageResult> delta = new HashMap<>(batch);
                                batch.clear();
                                applyCoverageUpdates(delta);
                            }
                        }
                    }

                    @Override
                    public void onFinished() {
                        coverageIndicatorRef.set(null);
                        coverageInProgress.set(false);
                        updateActionButtonsAsync();
                    }
                })
        );
    }

    public void updateExecutionResult(@NotNull String testFqn, int failureCount) {
        if (testFqn.isBlank()) return;
        int safeFailureCount = Math.max(0, failureCount);
        state.getOrCreate(testFqn).failureCount = safeFailureCount;

        List<ClassTestReportRow> updated = null;
        synchronized (rowsLock) {
            if (lastRows.isEmpty()) return;
            boolean changed = false;
            List<ClassTestReportRow> next = new ArrayList<>(lastRows.size());
            for (ClassTestReportRow row : lastRows) {
                if (!testFqn.equals(row.testFqn())) {
                    next.add(row);
                    continue;
                }
                ClassTestReportRow replacement = row.withFailureCount(safeFailureCount);
                next.add(replacement);
                if (replacement != row) changed = true;
            }
            if (!changed) return;
            next.sort(ROW_COMPARATOR);
            lastRows = Collections.unmodifiableList(next);
            updated = lastRows;
        }
        publishRows(updated);
    }

    private List<ClassTestReportRow> buildStructuralReport(@NotNull ProgressIndicator indicator) {
        CoverageCalculator coverage = new CoverageCalculator();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        List<String> cutFqns = findAllTopLevelClassFqns(indicator);
        List<ClassTestReportRow> out = new ArrayList<>(cutFqns.size());

        int i = 0;
        for (String cutFqn : cutFqns) {
            indicator.checkCanceled();
            indicator.setFraction(cutFqns.isEmpty() ? 1.0 : i / (double) cutFqns.size());
            indicator.setText2("Scanning " + cutFqn);

            CutBuildSnapshot snapshot = ReadAction.compute(
                    () -> buildCutSnapshot(cutFqn, coverage, psiFacade, scope)
            );
            i++;
            if (snapshot == null) continue;

            if (snapshot.testClasses.isEmpty()) {
                out.add(new ClassTestReportRow(
                        snapshot.cutFqn,
                        snapshot.cutSimpleName,
                        snapshot.cutPackageName,
                        null,
                        null,
                        snapshot.totalPublicMethods,
                        0,
                        0,
                        List.of(),
                        snapshot.cutPsi,
                        null,
                        ClassTestReportRow.CoverageStatus.NOT_ANALYZED
                ));
                continue;
            }

            for (PsiClass testClass : snapshot.testClasses) {
                String testFqn = testClass.getQualifiedName();
                out.add(new ClassTestReportRow(
                        snapshot.cutFqn,
                        snapshot.cutSimpleName,
                        snapshot.cutPackageName,
                        testFqn,
                        Objects.toString(testClass.getName(), ""),
                        snapshot.totalPublicMethods,
                        0,
                        failureCountFor(testFqn),
                        List.of(),
                        snapshot.cutPsi,
                        testClass,
                        ClassTestReportRow.CoverageStatus.NOT_ANALYZED
                ));
            }
        }

        out.sort(ROW_COMPARATOR);
        return out;
    }

    private @Nullable CutBuildSnapshot buildCutSnapshot(
            @NotNull String cutFqn,
            @NotNull CoverageCalculator coverage,
            @NotNull JavaPsiFacade psiFacade,
            @NotNull GlobalSearchScope scope
    ) {
        PsiClass cut = psiFacade.findClass(cutFqn, scope);
        if (cut == null || !cut.isValid()) return null;

        int totalPublicMethods = coverage.countCoverablePublicMethods(cut);
        if (totalPublicMethods <= 0) return null;

        List<PsiClass> testClasses = coverage.findTestClassesFor(cut);
        return new CutBuildSnapshot(
                cut,
                cutFqn,
                Objects.toString(cut.getName(), ""),
                packageOf(cutFqn),
                totalPublicMethods,
                testClasses
        );
    }

    private int failureCountFor(@Nullable String testFqn) {
        if (testFqn == null || testFqn.isBlank()) return 0;
        var testState = state.get(testFqn);
        return testState == null ? 0 : Math.max(0, testState.failureCount);
    }

    private List<String> findAllTopLevelClassFqns(@NotNull ProgressIndicator indicator) {
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        PsiManager psiManager = PsiManager.getInstance(project);

        Collection<VirtualFile> javaFiles = ReadAction.compute(
                () -> FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        );
        List<String> result = new ArrayList<>(javaFiles.size());
        int i = 0;
        for (VirtualFile file : javaFiles) {
            indicator.checkCanceled();
            if (i % 100 == 0) {
                indicator.setText2("Discovering classes: " + file.getName());
            }

            List<String> fileClasses = ReadAction.compute(() -> {
                if (!file.isValid()) return Collections.emptyList();
                if (!index.isInSourceContent(file) || index.isInTestSourceContent(file)) return Collections.emptyList();
                PsiFile psiFile = psiManager.findFile(file);
                if (!(psiFile instanceof PsiJavaFile javaFile)) return Collections.emptyList();

                List<String> out = new ArrayList<>();
                for (PsiClass cls : javaFile.getClasses()) {
                    String qn = cls.getQualifiedName();
                    if (qn != null && !qn.isBlank()) {
                        out.add(qn);
                    }
                }
                return out;
            });

            result.addAll(fileClasses);
            i++;
        }

        result.sort(Comparator.naturalOrder());
        return result;
    }

    private void applyCoverageUpdates(@NotNull Map<String, CoverageCalculator.CoverageResult> updatesByCut) {
        if (updatesByCut.isEmpty()) return;

        List<ClassTestReportRow> updated = null;
        synchronized (rowsLock) {
            if (lastRows.isEmpty()) return;
            boolean changed = false;
            List<ClassTestReportRow> next = new ArrayList<>(lastRows.size());
            for (ClassTestReportRow row : lastRows) {
                CoverageCalculator.CoverageResult cov = updatesByCut.get(row.cutFqn());
                if (cov == null) {
                    next.add(row);
                    continue;
                }
                ClassTestReportRow replacement = row.withCoverage(cov);
                next.add(replacement);
                if (replacement != row) changed = true;
            }
            if (!changed) return;
            next.sort(ROW_COMPARATOR);
            lastRows = Collections.unmodifiableList(next);
            updated = lastRows;
        }
        publishRows(updated);
    }

    private @Nullable CoverageCalculator.CoverageResult computeCoverageForCut(
            @NotNull CutCoverageWorkItem item,
            @NotNull CoverageCalculator coverage,
            @NotNull JavaPsiFacade psiFacade,
            @NotNull GlobalSearchScope scope
    ) {
        PsiClass cut = psiFacade.findClass(item.cutFqn, scope);
        if (cut == null || !cut.isValid()) return null;

        List<PsiClass> tests = new ArrayList<>(item.testFqns.size());
        for (String testFqn : item.testFqns) {
            if (testFqn == null || testFqn.isBlank()) continue;
            PsiClass testClass = psiFacade.findClass(testFqn, scope);
            if (testClass != null && testClass.isValid()) {
                tests.add(testClass);
            }
        }

        String cacheKey = buildCoverageCacheKey(cut, tests);
        if (cacheKey != null) {
            CoverageCalculator.CoverageResult cached = coverageCache.get(cacheKey);
            if (cached != null) return cached;
        }

        CoverageCalculator.CoverageResult computed = coverage.computePublicMethodCoverage(cut, tests);
        if (cacheKey != null) {
            coverageCache.put(cacheKey, computed);
        }
        return computed;
    }

    private List<CutCoverageWorkItem> snapshotCoverageWork() {
        List<ClassTestReportRow> rows = lastRows;
        if (rows.isEmpty()) return Collections.emptyList();

        Map<String, Set<String>> testsByCut = new LinkedHashMap<>();
        for (ClassTestReportRow row : rows) {
            String cutFqn = row.cutFqn();
            if (cutFqn == null || cutFqn.isBlank()) continue;
            Set<String> tests = testsByCut.computeIfAbsent(cutFqn, ignored -> new LinkedHashSet<>());
            String testFqn = row.testFqn();
            if (testFqn != null && !testFqn.isBlank()) {
                tests.add(testFqn);
            }
        }

        List<CutCoverageWorkItem> work = new ArrayList<>(testsByCut.size());
        for (Map.Entry<String, Set<String>> entry : testsByCut.entrySet()) {
            work.add(new CutCoverageWorkItem(entry.getKey(), new ArrayList<>(entry.getValue())));
        }
        return work;
    }

    private void updateRows(@NotNull List<ClassTestReportRow> rows, boolean keepOrder) {
        List<ClassTestReportRow> next = new ArrayList<>(rows);
        if (!keepOrder) {
            next.sort(ROW_COMPARATOR);
        }
        synchronized (rowsLock) {
            lastRows = Collections.unmodifiableList(next);
        }
    }

    private void publishRows(@NotNull List<ClassTestReportRow> rows) {
        ApplicationManager.getApplication().invokeLater(() -> publishRowsOnEdt(rows));
    }

    private void publishRowsOnEdt(@NotNull List<ClassTestReportRow> rows) {
        for (Consumer<List<ClassTestReportRow>> listener : listeners) {
            try {
                listener.accept(rows);
            } catch (Throwable ignored) {
                // Keep UI responsive even if a listener fails.
            }
        }
    }

    private void cancelCoverageAnalysis() {
        ProgressIndicator indicator = coverageIndicatorRef.get();
        if (indicator != null && !indicator.isCanceled()) {
            indicator.cancel();
        }
    }

    private static String buildCoverageCacheKey(PsiClass cut, List<PsiClass> testClasses) {
        PsiFile cutFile = cut.getContainingFile();
        VirtualFile cutVf = cutFile == null ? null : cutFile.getVirtualFile();
        if (cutVf == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(cutVf.getPath()).append('#').append(cutVf.getModificationStamp());

        List<String> testTokens = new ArrayList<>(testClasses.size());
        for (PsiClass testClass : testClasses) {
            if (testClass == null || !testClass.isValid()) continue;
            PsiFile testFile = testClass.getContainingFile();
            VirtualFile testVf = testFile == null ? null : testFile.getVirtualFile();
            if (testVf == null) continue;
            testTokens.add(testVf.getPath() + "#" + testVf.getModificationStamp());
        }
        Collections.sort(testTokens);
        for (String token : testTokens) {
            sb.append('|').append(token);
        }
        return sb.toString();
    }

    private static String packageOf(String fqn) {
        if (fqn == null) return "";
        int idx = fqn.lastIndexOf('.');
        return idx <= 0 ? "" : fqn.substring(0, idx);
    }

    /* =======================
       UI
       ======================= */

    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel centerPanel = new JPanel(new CardLayout());
    private final ReportTableModel tableModel = new ReportTableModel();
    private final JBTable table = new JBTable(tableModel);
    private final JBScrollPane tableScrollPane = new JBScrollPane(table);
    private final JLabel emptyState = new JLabel("No classes with public methods found.", SwingConstants.CENTER);
    private final JButton regenerateButton = new JButton("Refresh", AllIcons.Actions.Refresh);
    private final JButton analyzeCoverageButton = new JButton("Analyze Coverage", AllIcons.Actions.Execute);
    private final PillLabel healthLabel = new PillLabel("Test health: —", SwingConstants.CENTER);
    private final Icon baseIcon = IconLoader.getIcon("/icons/jaipilot.svg", ReportView.class);
    private final Icon staleIcon = new LayeredIcon(baseIcon, AllIcons.General.WarningDecorator);
    private volatile boolean stale = false;

    public JComponent getComponent() {
        return root;
    }

    private void buildUI() {
        root.setBorder(JBUI.Borders.empty());

        table.setRowHeight(JBUI.scale(28));
        table.setShowGrid(true);
        table.setGridColor(UIUtil.getTableGridColor());
        table.setIntercellSpacing(new Dimension(JBUI.scale(1), JBUI.scale(1)));
        table.setStriped(true);
        ReportTableRenderers.install(table, project);

        int twoLine = table.getFontMetrics(table.getFont()).getHeight() * 2 + JBUI.scale(4);
        int rowHeight = Math.max(JBUI.scale(32), twoLine);
        table.setRowHeight(rowHeight);

        emptyState.setBorder(JBUI.Borders.empty(32));
        emptyState.setForeground(JBColor.GRAY);
        emptyState.setFont(emptyState.getFont().deriveFont(Font.BOLD, emptyState.getFont().getSize() + 1));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(JBUI.Borders.empty(8, 12));

        healthLabel.setVisible(false);

        regenerateButton.addActionListener(e -> refreshAsync("manual"));
        analyzeCoverageButton.addActionListener(e -> analyzeCoverageAsync());

        JPanel actionsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(12), 0));
        actionsLeft.setOpaque(false);
        actionsLeft.add(regenerateButton);
        actionsLeft.add(analyzeCoverageButton);

        JPanel actionsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        actionsRight.setOpaque(false);
        actionsRight.add(healthLabel);

        JPanel actionsRow = new JPanel(new BorderLayout());
        actionsRow.setOpaque(false);
        actionsRow.add(actionsLeft, BorderLayout.WEST);
        actionsRow.add(actionsRight, BorderLayout.EAST);

        header.add(actionsRow, BorderLayout.CENTER);

        centerPanel.add(tableScrollPane, "table");
        centerPanel.add(emptyState, "empty");
        root.add(header, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
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

        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
                PsiFile file = event.getFile();
                if (file instanceof PsiJavaFile) {
                    markStale();
                }
            }
        }, this);

        VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void contentsChanged(@NotNull VirtualFileEvent event) {
                maybeMarkStale(event.getFile());
            }

            @Override
            public void fileDeleted(@NotNull VirtualFileEvent event) {
                maybeMarkStale(event.getFile());
            }

            @Override
            public void fileCreated(@NotNull VirtualFileEvent event) {
                maybeMarkStale(event.getFile());
            }

            private void maybeMarkStale(VirtualFile file) {
                if (file != null && "java".equalsIgnoreCase(file.getExtension())) {
                    markStale();
                }
            }
        }, this);
    }

    private void onRowsUpdated(List<ClassTestReportRow> rows) {
        List<ClassTestReportRow> allRows = rows == null ? Collections.emptyList() : rows;
        tableModel.setRows(allRows);
        updateSummary(tableModel.getFilteredRows());
        showEmptyState(allRows.isEmpty());
        updateHealthIndicator(allRows);
        updateActionButtons();
    }

    private void updateSummary(List<ClassTestReportRow> rows) {}

    private void showEmptyState(boolean empty) {
        CardLayout cl = (CardLayout) centerPanel.getLayout();
        cl.show(centerPanel, empty ? "empty" : "table");
    }

    private void updateHealthIndicator(@NotNull List<ClassTestReportRow> rows) {
        if (rows.isEmpty()) {
            healthLabel.setVisible(false);
            return;
        }

        Map<String, CutHealthSummary> summaryByCut = new HashMap<>();
        for (ClassTestReportRow row : rows) {
            CutHealthSummary summary = summaryByCut.computeIfAbsent(
                    row.cutFqn(),
                    k -> new CutHealthSummary()
            );
            summary.absorb(row);
        }

        double scoreSum = 0.0;
        for (CutHealthSummary s : summaryByCut.values()) {
            scoreSum += s.healthScore();
        }
        double averageScore = summaryByCut.isEmpty() ? 0.0 : scoreSum / summaryByCut.size();

        String text;
        JBColor bg;
        JBColor fg;
        if (averageScore >= 90.0) {
            text = "Test health: Perfect";
            bg = new JBColor(new Color(223, 243, 229), new Color(60, 90, 70));
            fg = new JBColor(new Color(30, 120, 70), new Color(200, 255, 220));
        } else if (averageScore >= 75.0) {
            text = "Test health: Good";
            bg = new JBColor(new Color(230, 244, 230), new Color(70, 90, 70));
            fg = new JBColor(new Color(50, 120, 70), new Color(200, 230, 200));
        } else if (averageScore >= 50.0) {
            text = "Test health: Moderate";
            bg = new JBColor(new Color(255, 244, 214), new Color(90, 80, 50));
            fg = new JBColor(new Color(150, 110, 20), new Color(255, 230, 180));
        } else {
            text = "Test health: Poor";
            bg = new JBColor(new Color(255, 230, 230), new Color(90, 50, 50));
            fg = new JBColor(new Color(160, 40, 40), new Color(255, 200, 200));
        }
        healthLabel.setText(text + " (" + Math.round(averageScore) + "%)");
        healthLabel.setBackground(bg);
        healthLabel.setForeground(fg);
        healthLabel.setVisible(true);
    }

    private void updateActionButtons() {
        boolean refreshBusy = refreshInProgress.get();
        boolean coverageBusy = coverageInProgress.get();
        boolean hasRows = !lastRows.isEmpty();

        regenerateButton.setEnabled(!refreshBusy);
        analyzeCoverageButton.setEnabled(!refreshBusy && !coverageBusy && hasRows);

        if (coverageBusy) {
            analyzeCoverageButton.setText("Analyzing...");
        } else {
            analyzeCoverageButton.setText("Analyze Coverage");
        }
    }

    private void updateActionButtonsAsync() {
        ApplicationManager.getApplication().invokeLater(this::updateActionButtons);
    }

    private static final class CutHealthSummary {
        private boolean hasCoverage;
        private int totalMethods;
        private Set<String> uncoveredIntersection = new HashSet<>();
        private boolean missingTests;
        private boolean executionFailures;

        void absorb(ClassTestReportRow row) {
            missingTests = missingTests || row.isMissingTestClass();
            executionFailures = executionFailures || row.hasExecutionFailures();

            if (row.coverageStatus() != ClassTestReportRow.CoverageStatus.ANALYZED) {
                return;
            }
            if (!hasCoverage) {
                hasCoverage = true;
                totalMethods = row.totalPublicMethods();
                uncoveredIntersection = new HashSet<>(row.uncoveredMethodSignatures());
                return;
            }
            uncoveredIntersection.retainAll(row.uncoveredMethodSignatures());
        }

        double healthScore() {
            double score = 100.0;
            if (hasCoverage && totalMethods > 0) {
                int covered = Math.max(0, totalMethods - uncoveredIntersection.size());
                score = (covered * 100.0) / totalMethods;
            }
            if (missingTests) score *= 0.5;
            if (executionFailures) score *= 0.35;
            return Math.max(0.0, Math.min(100.0, score));
        }
    }

    private void markStale() {
        if (stale) return;
        stale = true;
        coverageCache.clear();
        updateStatusLine();
        updateToolWindowIcon(staleIcon);
    }

    private void markFresh() {
        stale = false;
        updateStatusLine();
        updateToolWindowIcon(baseIcon);
    }

    private void updateToolWindowIcon(Icon icon) {
        ApplicationManager.getApplication().invokeLater(() -> {
            var toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    .getToolWindow("JAIPilot Console");
            if (toolWindow != null && icon != null) {
                toolWindow.setIcon(icon);
            }
        });
    }

    private void updateStatusLine() {
        if (stale) {
            regenerateButton.setText("Re-analyse Stale Tests");
            regenerateButton.setIcon(AllIcons.Actions.ForceRefresh);
            return;
        }
        regenerateButton.setText("Refresh");
        regenerateButton.setIcon(AllIcons.Actions.Refresh);
    }

    private void openPsi(PsiClass psiClass) {
        PsiFile file = psiClass.getContainingFile();
        if (file == null) return;
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) return;
        new OpenFileDescriptor(project, vf, psiClass.getTextOffset()).navigate(true);
    }

    @Override
    public void dispose() {}

    private record CutBuildSnapshot(
            @NotNull PsiClass cutPsi,
            @NotNull String cutFqn,
            @NotNull String cutSimpleName,
            @NotNull String cutPackageName,
            int totalPublicMethods,
            @NotNull List<PsiClass> testClasses
    ) {}

    private record CutCoverageWorkItem(
            @NotNull String cutFqn,
            @NotNull List<String> testFqns
    ) {}

    /** Simple pill-style label with rounded background. */
    private static final class PillLabel extends JLabel {
        PillLabel(String text, int alignment) {
            super(text, alignment);
            setOpaque(false);
            setBorder(JBUI.Borders.empty(4, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = getBackground() == null ? getParent().getBackground() : getBackground();
            g2.setColor(bg);
            int arc = JBUI.scale(12);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
