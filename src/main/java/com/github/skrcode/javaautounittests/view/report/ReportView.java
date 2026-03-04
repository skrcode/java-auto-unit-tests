package com.github.skrcode.javaautounittests.view.report;

import com.github.skrcode.javaautounittests.dto.ClassTestReportRow;
import com.github.skrcode.javaautounittests.service.ReportState;
import com.github.skrcode.javaautounittests.util.CoverageCalculator;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.ProgressIndicator;
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
import com.intellij.psi.*;
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
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicBoolean refreshPending = new AtomicBoolean(false);
    private final Map<String, CoverageCalculator.CoverageResult> coverageCache = new HashMap<>();

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
        if (!refreshInProgress.compareAndSet(false, true)) {
            refreshPending.set(true);
            return;
        }

        DumbService.getInstance(project).runWhenSmart(() ->
                ProgressManager.getInstance().run(
                        new Task.Backgroundable(project,
                                "JAIPilot: Building Test Report", false) {

                            @Override
                            public void run(@NotNull ProgressIndicator indicator) {
                                indicator.setIndeterminate(false);

                                List<ClassTestReportRow> rows = Collections.emptyList();
                                try {
                                    rows = ReadAction.compute(() -> buildReport(indicator));
                                    lastRows = rows;
                                } catch (Throwable ignored) {
                                    rows = Collections.emptyList();
                                    lastRows = rows;
                                }

                                List<ClassTestReportRow> finalRows = rows;
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    for (Consumer<List<ClassTestReportRow>> l : listeners) {
                                        try {
                                            l.accept(finalRows);
                                        } catch (Throwable ignored) {}
                                    }
                                    refreshInProgress.set(false);
                                    if (refreshPending.getAndSet(false)) {
                                        refreshAsync("coalesced");
                                    }
                                });
                            }
                        })
        );
    }

    private List<ClassTestReportRow> buildReport(@NotNull ProgressIndicator indicator) {
        CoverageCalculator coverage = new CoverageCalculator();

        List<PsiClass> cuts = findAllClasses();
        List<ClassTestReportRow> out = new ArrayList<>(cuts.size());

        int i = 0;
        for (PsiClass cut : cuts) {
            indicator.checkCanceled();
            indicator.setFraction(cuts.isEmpty() ? 1.0 : i / (double) cuts.size());
            indicator.setText2("Scanning " + safeName(cut));

            String fqn = Objects.toString(cut.getQualifiedName(), "");
            String simple = Objects.toString(cut.getName(), "");
            String pkg = packageOf(fqn);

            if (coverage.countCoverablePublicMethods(cut) <= 0) {
                i++;
                continue;
            }

            List<PsiClass> testClasses = coverage.findTestClassesFor(cut);
            CoverageCalculator.CoverageResult cov = getCoverageWithCache(coverage, cut, testClasses);

            if (testClasses.isEmpty()) {
                out.add(new ClassTestReportRow(
                        fqn,
                        simple,
                        pkg,
                        null,
                        null,
                        cov.totalPublicMethods,
                        cov.coveredPublicMethods,
                        0,
                        cov.uncoveredMethodSignatures,
                        cut,
                        null
                ));
                i++;
                continue;
            }

            for (PsiClass testClass : testClasses) {
                String testFqn = testClass.getQualifiedName();
                String testSimple = testClass.getName();

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
            }
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

    private List<PsiClass> findAllClasses() {
        List<PsiClass> result = new ArrayList<>();
        ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        PsiManager psiManager = PsiManager.getInstance(project);
        Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));

        for (VirtualFile file : javaFiles) {
            if (!index.isInSourceContent(file) || index.isInTestSourceContent(file)) continue;
            PsiFile psiFile = psiManager.findFile(file);
            if (!(psiFile instanceof PsiJavaFile javaFile)) continue;
            for (PsiClass cls : javaFile.getClasses()) {
                if (cls.getQualifiedName() != null) {
                    result.add(cls);
                }
            }
        }

        result.sort(Comparator.comparing(c -> Objects.toString(c.getQualifiedName(), "")));
        return result;
    }

    private static String packageOf(String fqn) {
        if (fqn == null) return "";
        int idx = fqn.lastIndexOf('.');
        return idx <= 0 ? "" : fqn.substring(0, idx);
    }

    private CoverageCalculator.CoverageResult getCoverageWithCache(
            CoverageCalculator coverage,
            PsiClass cut,
            List<PsiClass> testClasses
    ) {
        String cacheKey = buildCoverageCacheKey(cut, testClasses);
        if (cacheKey == null) {
            return coverage.computePublicMethodCoverage(cut, testClasses);
        }
        CoverageCalculator.CoverageResult cached = coverageCache.get(cacheKey);
        if (cached != null) return cached;

        CoverageCalculator.CoverageResult computed = coverage.computePublicMethodCoverage(cut, testClasses);
        coverageCache.put(cacheKey, computed);
        return computed;
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

        // Keep rows compact but tall enough for two lines (name + package)
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

        JPanel actionsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(12), 0));
        actionsLeft.setOpaque(false);
        actionsLeft.add(regenerateButton);

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
        markFresh();
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
                    k -> CutHealthSummary.from(row)
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

    private static final class CutHealthSummary {
        private final int totalMethods;
        private Set<String> uncoveredIntersection;
        private boolean missingTests;
        private boolean executionFailures;

        private CutHealthSummary(int totalMethods, Set<String> uncoveredIntersection, boolean missingTests, boolean executionFailures) {
            this.totalMethods = totalMethods;
            this.uncoveredIntersection = uncoveredIntersection;
            this.missingTests = missingTests;
            this.executionFailures = executionFailures;
        }

        static CutHealthSummary from(ClassTestReportRow row) {
            return new CutHealthSummary(
                    row.totalPublicMethods(),
                    new HashSet<>(row.uncoveredMethodSignatures()),
                    row.isMissingTestClass(),
                    row.hasExecutionFailures()
            );
        }

        void absorb(ClassTestReportRow row) {
            uncoveredIntersection.retainAll(row.uncoveredMethodSignatures());
            missingTests = missingTests || row.isMissingTestClass();
            executionFailures = executionFailures || row.hasExecutionFailures();
        }

        double healthScore() {
            if (totalMethods <= 0) return 100.0;

            int covered = Math.max(0, totalMethods - uncoveredIntersection.size());
            double score = (covered * 100.0) / totalMethods;
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

    private void openPsi(PsiElement el) {
        PsiFile f = el.getContainingFile();
        if (f == null) return;
        VirtualFile vf = f.getVirtualFile();
        if (vf == null) return;
        new OpenFileDescriptor(project, vf, el.getTextOffset()).navigate(true);
    }

    @Override
    public void dispose() {}

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
