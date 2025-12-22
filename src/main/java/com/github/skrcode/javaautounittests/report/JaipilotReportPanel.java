package com.github.skrcode.javaautounittests.report;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public final class JaipilotReportPanel implements Disposable {

    private static volatile JaipilotReportPanel ACTIVE;

    public static JaipilotReportPanel getActiveInstance(Project project) {
        return ACTIVE;
    }

    private final Project project;
    private final JPanel root = new JPanel(new BorderLayout());

    private final JBLabel summary = new JBLabel();
    private final JBTextField filter = new JBTextField();

    private final ReportTableModel tableModel = new ReportTableModel();
    private final JBTable table = new JBTable(tableModel);

    private final JBTextArea details = new JBTextArea();

    public JaipilotReportPanel(Project project) {
        this.project = project;
        ACTIVE = this;

        buildUI();
        hookListeners();

        JaipilotReportService
                .getInstance(project)
                .addListener(this, this::onRowsUpdated);
    }

    public JComponent getComponent() {
        return root;
    }

    public List<ClassTestReportRow> getSelectedRows() {
        int[] rows = table.getSelectedRows();
        return tableModel.getRowsAt(rows);
    }

    private void buildUI() {
        root.setBorder(JBUI.Borders.empty());

        // Toolbar
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(ActionManager.getInstance().getAction("JAIPilot.Report.RunEvaluator"));
        group.add(ActionManager.getInstance().getAction("JAIPilot.Report.Refresh"));

        ActionToolbar toolbar =
                ActionManager.getInstance()
                        .createActionToolbar("JAIPilot.Report.Toolbar", group, true);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(JBUI.Borders.empty(6));
        top.add(toolbar.getComponent(), BorderLayout.WEST);

        filter.getEmptyText().setText("Filter classes / tests / packages");
        top.add(filter, BorderLayout.CENTER);

        root.add(top, BorderLayout.NORTH);

        // Summary
        summary.setBorder(JBUI.Borders.empty(4, 8));
        summary.setFont(summary.getFont().deriveFont(Font.PLAIN));
        root.add(summary, BorderLayout.PAGE_START);

        // Table
        table.setRowHeight(JBUI.scale(28));
        table.setShowGrid(false);
        table.setStriped(true);
        ReportTableRenderers.install(table);

        // Details
        details.setEditable(false);
        details.setFont(UIUtil.getLabelFont());
        details.setBorder(JBUI.Borders.empty(8));

        JBSplitter splitter = new JBSplitter(true, 0.75f);
        splitter.setFirstComponent(new JBScrollPane(table));
        splitter.setSecondComponent(new JBScrollPane(details));

        root.add(splitter, BorderLayout.CENTER);
    }

    private void hookListeners() {
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onFilterChanged();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onFilterChanged();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onFilterChanged();
            }

            private void onFilterChanged() {
                tableModel.setFilterText(filter.getText());
                updateSummary(tableModel.getFilteredRows());
            }
        });


        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetails(tableModel.getRowAt(table.getSelectedRow()));
            }
        });

        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent event) {
                ClassTestReportRow row =
                        tableModel.getRowAt(table.getSelectedRow());
                if (row != null && row.getCutPsi() != null) {
                    ReportUiNav.openPsi(project, row.getCutPsi());
                }
                return true;
            }
        }.installOn(table);
    }

    private void onRowsUpdated(List<ClassTestReportRow> rows) {
        tableModel.setRows(rows);
        updateSummary(tableModel.getFilteredRows());
    }

    private void updateSummary(List<ClassTestReportRow> rows) {
        int classes = rows.size();
        int missing = 0;
        int failing = 0;
        int totalMethods = 0;
        int coveredMethods = 0;

        for (ClassTestReportRow r : rows) {
            if (r.isMissingTestClass()) missing++;
            if (r.getLastTestStatus() == TestRunStatus.FAIL) failing++;
            totalMethods += r.getTotalPublicMethods();
            coveredMethods += r.getCoveredPublicMethods();
        }

        int pct = totalMethods == 0 ? 100 :
                (int) Math.round((coveredMethods * 100.0) / totalMethods);

        summary.setText(
                "Classes: " + classes +
                        "   Missing tests: " + missing +
                        "   Method coverage: " + pct + "%" +
                        "   Failing: " + failing
        );
    }

    private void showDetails(ClassTestReportRow row) {
        if (row == null) {
            details.setText("Select a class to see details.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Class: ").append(row.getCutFqn()).append("\n");
        sb.append("Test: ").append(row.getTestFqn() == null ? "—" : row.getTestFqn()).append("\n\n");

        sb.append("Public methods covered: ")
                .append(row.getCoveredPublicMethods())
                .append(" / ")
                .append(row.getTotalPublicMethods())
                .append("\n");

        sb.append("Last test status: ").append(row.getLastTestStatus()).append("\n\n");

        if (!row.getUncoveredMethodSignatures().isEmpty()) {
            sb.append("Uncovered methods:\n");
            for (String m : row.getUncoveredMethodSignatures()) {
                sb.append("  - ").append(m).append("\n");
            }
        }

        details.setText(sb.toString());
        details.setCaretPosition(0);
    }

    @Override
    public void dispose() {
        if (ACTIVE == this) ACTIVE = null;
    }
}
