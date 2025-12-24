package com.github.skrcode.javaautounittests.report;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ReportTableRenderers {

    public static void install(JBTable table, Project project) {
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(36));
        RunAgainCell runCell = new RunAgainCell(project, table);
        table.getColumnModel().getColumn(0).setCellRenderer(runCell);
        table.getColumnModel().getColumn(0).setCellEditor(runCell);
        table.getColumnModel().getColumn(0).setHeaderRenderer(new RunAllHeader(project, table));

        table.getColumnModel().getColumn(1).setCellRenderer(new CutRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new TestRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new CoverageRenderer());
    }

    private static final class RunAgainCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {
        private final JButton button = new JButton(AllIcons.Actions.Execute);
        private final Project project;
        private final JBTable table;
        private ClassTestReportRow currentRow;

        RunAgainCell(Project project, JBTable table) {
            this.project = project;
            this.table = table;
            button.setOpaque(true);
            button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            button.addActionListener(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentRow == null) return;
            if (currentRow.testFqn() == null) return;
            RunEvaluatorAction.runTestClass(project, currentRow.testFqn());
            fireEditingStopped();
        }

        private Component prepare(JTable table, Object value, boolean isSelected, int row) {
            currentRow = (ClassTestReportRow) value;
            boolean failing = currentRow != null && currentRow.lastFailureCount() > 0;

            button.setEnabled(failing && currentRow.testFqn() != null);
            button.setToolTipText(failing ? "Re-run failing tests for this class" : "No recent failures");
//            button.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return button;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return prepare(table, value, isSelected, row);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            int modelRow = this.table.convertRowIndexToModel(row);
            Object val = this.table.getModel().getValueAt(modelRow, column);
            return prepare(table, val, true, row);
        }

        @Override
        public Object getCellEditorValue() {
            return currentRow;
        }
    }

    private static final class RunAllHeader extends JButton implements TableCellRenderer {
        private final Project project;
        private final JBTable table;

        RunAllHeader(Project project, JBTable table) {
            super(AllIcons.Actions.Execute);
            this.project = project;
            this.table = table;
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(true);
            setToolTipText("Run all failing tests");
            addActionListener(e -> {
                // Gather failing rows and re-run them
                var model = table.getModel();
                if (!(model instanceof ReportTableModel reportModel)) return;
                java.util.List<ClassTestReportRow> failing =
                        reportModel.getFilteredRows()
                                .stream()
                                .filter(r -> r.lastFailureCount() > 0 && r.testFqn() != null)
                                .toList();
                RunEvaluatorAction.runTests(project, failing);
            });
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            return this;
        }
    }

    private static final class CutRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            SimpleColoredComponent c = new SimpleColoredComponent();
            c.setOpaque(true);
            renderPath(c, r.cutPackageName(), r.cutSimpleName(), true);
            c.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return c;
        }
    }

    private static final class TestRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            SimpleColoredComponent c = new SimpleColoredComponent();
            c.setOpaque(true);

            if (r.isMissingTestClass()) {
                c.append("—", SimpleTextAttributes.ERROR_ATTRIBUTES);
                c.append("  (missing)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else {
                renderPath(c, r.testFqn() == null ? "" : pkgPart(r.testFqn()), r.testSimpleName(), false);
            }
            c.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return c;
        }
    }

    private static final class CoverageRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;

            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(true);
            p.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

            int total = r.totalPublicMethods();
            int cov = r.coveredPublicMethods();
            String text = cov + " / " + total;

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setBorderPainted(false);
            bar.setStringPainted(true);
            bar.setString(text);

            int pct = total <= 0 ? 100 : (int) Math.round((cov * 100.0) / total);
            bar.setValue(Math.max(0, Math.min(100, pct)));

            p.add(bar, BorderLayout.CENTER);
            return p;
        }
    }

    private static void renderPath(SimpleColoredComponent c, String pkg, String name, boolean boldName) {

        if (pkg != null && !pkg.isBlank()) {
            String[] parts = pkg.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                c.append(parts[i], SimpleTextAttributes.GRAYED_ATTRIBUTES);
                if (i < parts.length - 1) {
                    c.append("/", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    c.append("/", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
                }
            }
        }
        if (name != null && !name.isBlank()) {
            c.append(name, boldName ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    private static String pkgPart(String fqn) {
        if (fqn == null) return "";
        int idx = fqn.lastIndexOf('.');
        return idx > 0 ? fqn.substring(0, idx) : "";
    }
}
