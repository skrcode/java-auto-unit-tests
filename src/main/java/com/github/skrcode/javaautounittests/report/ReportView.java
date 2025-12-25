package com.github.skrcode.javaautounittests.report;

import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
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
import java.util.function.Consumer;

public final class ReportView implements Disposable {

    private final Project project;
    private final ReportState state = new ReportState();

    private final List<Consumer<List<ClassTestReportRow>>> listeners =
            new CopyOnWriteArrayList<>();

    private volatile List<ClassTestReportRow> lastRows = Collections.emptyList();
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
        CoverageCalculator coverage = new CoverageCalculator(project);

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

            List<PsiClass> testClasses = coverage.findTestClassesFor(cut);
            if (testClasses.isEmpty()) {
                CoverageCalculator.CoverageResult cov = coverage.computePublicMethodCoverage(cut, null);
                if (cov.totalPublicMethods > 0) {
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
                }
                i++;
                continue;
            }

            for (PsiClass testClass : testClasses) {
                CoverageCalculator.CoverageResult cov = coverage.computePublicMethodCoverage(cut, testClass);
                if (cov.totalPublicMethods <= 0) {
                    continue; // Skip classes with no public methods
                }

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
    private final JLabel emptyState = new JLabel("All good! No failing classes found.", SwingConstants.CENTER);
    private final JButton regenerateButton = new JButton("Force Analyse Tests", AllIcons.Actions.Refresh);
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
        JButton fixAllButton = new JButton("Fix Tests for all classes", AllIcons.Actions.RunAll);
        fixAllButton.addActionListener(e -> {
            var model = tableModel.getFilteredRows();
            var runnable = model.stream()
                    .filter(r -> r.hasFailures() && r.testFqn() != null)
                    .toList();
            RunEvaluatorAction.runTests(project, runnable);
        });

        JPanel actionsLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(12), 0));
        actionsLeft.setOpaque(false);
        actionsLeft.add(regenerateButton);
        actionsLeft.add(fixAllButton);

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
        List<ClassTestReportRow> failing = rows == null
                ? Collections.emptyList()
                : rows.stream().filter(ClassTestReportRow::hasFailures).toList();

        tableModel.setRows(failing);
        updateSummary(tableModel.getFilteredRows());
        showEmptyState(failing.isEmpty());
        updateHealthIndicator(failing.size(), rows == null ? 0 : rows.size());
        markFresh();
    }

    private void updateSummary(List<ClassTestReportRow> rows) {}

    private void showEmptyState(boolean empty) {
        CardLayout cl = (CardLayout) centerPanel.getLayout();
        cl.show(centerPanel, empty ? "empty" : "table");
    }

    private void updateHealthIndicator(int failing, int total) {
        if (total <= 0) {
            healthLabel.setVisible(false);
            return;
        }
        double ratio = total == 0 ? 0.0 : (failing / (double) total);
        String text;
        JBColor bg;
        JBColor fg;
        if (failing == 0) {
            text = "Test health: Perfect";
            bg = new JBColor(new Color(223, 243, 229), new Color(60, 90, 70));
            fg = new JBColor(new Color(30, 120, 70), new Color(200, 255, 220));
        } else if (ratio > 0.66) {
            text = "Test health: Poor";
            bg = new JBColor(new Color(255, 230, 230), new Color(90, 50, 50));
            fg = new JBColor(new Color(160, 40, 40), new Color(255, 200, 200));
        } else if (ratio > 0.33) {
            text = "Test health: Moderate";
            bg = new JBColor(new Color(255, 244, 214), new Color(90, 80, 50));
            fg = new JBColor(new Color(150, 110, 20), new Color(255, 230, 180));
        } else {
            text = "Test health: Good";
            bg = new JBColor(new Color(230, 244, 230), new Color(70, 90, 70));
            fg = new JBColor(new Color(50, 120, 70), new Color(200, 230, 200));
        }
        healthLabel.setText(text);
        healthLabel.setBackground(bg);
        healthLabel.setForeground(fg);
        healthLabel.setVisible(true);
    }

    private void markStale() {
        if (stale) return;
        stale = true;
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
        regenerateButton.setText("Force Analyse Tests");
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
