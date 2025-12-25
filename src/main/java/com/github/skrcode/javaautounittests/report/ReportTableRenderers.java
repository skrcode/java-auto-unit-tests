package com.github.skrcode.javaautounittests.report;

import com.github.skrcode.javaautounittests.actions.GenerateTestAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ReportTableRenderers {

    public static void install(JBTable table, Project project) {
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(64));
        RunAgainCell runCell = new RunAgainCell(project, table);
        table.getColumnModel().getColumn(0).setCellRenderer(runCell);
        table.getColumnModel().getColumn(0).setCellEditor(runCell);

        table.getColumnModel().getColumn(1).setCellRenderer(new CutRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new TestRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new CoverageRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new FailureRenderer());
        table.getColumnModel().getColumn(3).setHeaderRenderer(new RightAlignedHeader(table));
        table.getColumnModel().getColumn(4).setHeaderRenderer(new RightAlignedHeader(table));
    }

    private static final class RunAgainCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {
        private final JButton button = new JButton("Fix", AllIcons.Actions.Execute);
        private final JPanel empty = new JPanel();
        private final Project project;
        private final JBTable table;
        private ClassTestReportRow currentRow;

        RunAgainCell(Project project, JBTable table) {
            this.project = project;
            this.table = table;
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            button.setFocusPainted(false);
            button.setRolloverEnabled(false);
            button.setMargin(new Insets(0, 0, 0, 0));
            int size = JBUI.scale(20);
            Dimension d = new Dimension(size, size);
            button.setPreferredSize(d);
            button.setMinimumSize(d);
            button.setMaximumSize(d);
            button.addActionListener(this);

            empty.setOpaque(false);
            empty.setPreferredSize(d);
            empty.setMinimumSize(d);
            empty.setMaximumSize(d);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentRow == null) return;
            if (currentRow.cutPsi() == null) return;
            GenerateTestAction.runForClass(project, currentRow.cutPsi());
            fireEditingStopped();
        }

        private Component rendererComponent(JTable table, Object value, boolean isSelected, int row) {
            ClassTestReportRow rowData = (ClassTestReportRow) value;
            boolean failing = rowData != null && rowData.hasFailures();
            if (!failing) {
                empty.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return empty;
            }
            boolean runnable = rowData.cutPsi() != null;
            button.setEnabled(runnable);
            button.setToolTipText(runnable ? "Generate tests for this class" : null);
            button.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return button;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return rendererComponent(table, value, isSelected, row);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            int modelRow = this.table.convertRowIndexToModel(row);
            Object val = this.table.getModel().getValueAt(modelRow, column);
            currentRow = (ClassTestReportRow) val;
            return rendererComponent(table, val, true, row);
        }

        @Override
        public boolean isCellEditable(java.util.EventObject e) {
            if (!(e instanceof java.awt.event.MouseEvent me)) {
                return false;
            }
            int viewRow = table.rowAtPoint(me.getPoint());
            int viewCol = table.columnAtPoint(me.getPoint());
            if (viewRow < 0 || viewCol != 0) return false;
            ClassTestReportRow rowData = tableModelRow(viewRow);
            return rowData != null && rowData.hasFailures() && rowData.cutPsi() != null;
        }

        private ClassTestReportRow tableModelRow(int viewRow) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            Object val = table.getModel().getValueAt(modelRow, 0);
            return (val instanceof ClassTestReportRow rowData) ? rowData : null;
        }

        @Override
        public Object getCellEditorValue() {
            return currentRow;
        }
    }

    private static final class RightAlignedHeader implements TableCellRenderer {
        private final JLabel label = new JLabel("", SwingConstants.RIGHT);

        RightAlignedHeader(JTable table) {
            label.setBorder(JBUI.Borders.empty(0, 6));
            label.setFont(table.getTableHeader().getFont());
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            label.setText(value == null ? "" : value.toString());
            return label;
        }
    }

    private static final class CutRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JPanel inner = new JPanel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel pkgLabel = new JLabel();

        CutRenderer() {
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setOpaque(false);
            nameLabel.setBorder(JBUI.Borders.empty(0, 6, 0, 6));
            pkgLabel.setBorder(JBUI.Borders.empty(0, 6, 0, 6));
            panel.add(inner, BorderLayout.WEST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            nameLabel.setText(r.cutSimpleName() == null ? "" : r.cutSimpleName());
            nameLabel.setFont(nameLabel.getFont()); // default size/weight
            nameLabel.setForeground(UIUtil.getLabelForeground());

            pkgLabel.setText(wrapPackage(r.cutPackageName()));
            pkgLabel.setForeground(JBColor.GRAY);
            pkgLabel.setFont(pkgLabel.getFont()); // default size/weight

            inner.removeAll();
            inner.add(nameLabel);
            inner.add(pkgLabel);
            panel.setToolTipText(r.cutFqn());
            nameLabel.setToolTipText(r.cutFqn());
            pkgLabel.setToolTipText(r.cutFqn());
            return panel;
        }
    }

    private static final class TestRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JPanel inner = new JPanel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel pkgLabel = new JLabel();
        private final JLabel missing = new JLabel("", SwingConstants.CENTER);

        TestRenderer() {
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setOpaque(false);
            nameLabel.setBorder(JBUI.Borders.empty(0, 6, 0, 6));
            pkgLabel.setBorder(JBUI.Borders.empty(0, 6, 0, 6));
            panel.add(inner, BorderLayout.WEST);

            missing.setBorder(JBUI.Borders.empty(4, 8));
            missing.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            if (r.isMissingTestClass()) {
                Color bg = isSelected ? table.getSelectionBackground() : new JBColor(new Color(255, 236, 232), new Color(70, 60, 60));
                Color fg = isSelected ? table.getSelectionForeground() : new JBColor(new Color(160, 60, 40), new Color(255, 200, 200));
                missing.setText("No test class found");
                missing.setBackground(bg);
                missing.setForeground(fg);
                return missing;
            }

            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            nameLabel.setText(r.testSimpleName() == null ? "" : r.testSimpleName());
            nameLabel.setFont(nameLabel.getFont()); // revert to default size/weight
            nameLabel.setForeground(UIUtil.getLabelForeground());

            String pkg = r.testFqn() == null ? "" : pkgPart(r.testFqn());
            pkgLabel.setText(wrapPackage(pkg));
            pkgLabel.setForeground(JBColor.GRAY);
            pkgLabel.setFont(pkgLabel.getFont()); // default size/weight

            inner.removeAll();
            inner.add(nameLabel);
            inner.add(pkgLabel);
            panel.setToolTipText(r.testFqn());
            nameLabel.setToolTipText(r.testFqn());
            pkgLabel.setToolTipText(r.testFqn());
            return panel;
        }
    }

    private static final class CoverageRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JLabel label = new JLabel("", SwingConstants.RIGHT);

        CoverageRenderer() {
            label.setBorder(JBUI.Borders.empty(0, 6));
            label.setFont(UIUtil.getLabelFont());
            panel.add(label, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;

            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

            int total = r.totalPublicMethods();
            int cov = r.coveredPublicMethods();
            int pct = total <= 0 ? 0 : (int) Math.round((cov * 100.0) / total);
            pct = Math.max(0, Math.min(100, pct));
            label.setText(pct + "%");
            label.setToolTipText(cov + " / " + total + " public methods tested");

            return panel;
        }
    }

    private static final class FailureRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JLabel label = new JLabel("", SwingConstants.RIGHT);

        FailureRenderer() {
            label.setBorder(JBUI.Borders.empty(0, 6));
            label.setFont(UIUtil.getLabelFont());
            panel.add(label, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            if (r == null || r.isMissingTestClass()) {
                label.setText("—");
                label.setToolTipText("No test class found");
                return panel;
            }

            // With current data we only know if the last run failed; treat any failure as 0% success.
            int successPct = r.lastFailureCount() > 0 ? 0 : 100;
            label.setText(successPct + "%");
            label.setToolTipText("Assumed from last run result");
            return panel;
        }
    }

    private static String wrapPackage(String pkg) {
        if (pkg == null) return "";
        String escaped = pkg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><span style='color:" +
                (UIUtil.isUnderDarcula() ? "#A6A6A6" : "#666666") +
                "; font-size:smaller;'>" +
                escaped.replace(".", ".&#8203;") +
                "</span></html>";
    }

    private static String pkgPart(String fqn) {
        if (fqn == null) return "";
        int idx = fqn.lastIndexOf('.');
        return idx > 0 ? fqn.substring(0, idx) : "";
    }
}
