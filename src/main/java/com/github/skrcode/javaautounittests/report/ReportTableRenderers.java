package com.github.skrcode.javaautounittests.report;

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
            boolean failing = currentRow != null && hasFailure(currentRow);
            boolean runnable = failing && currentRow.testFqn() != null;

            button.setVisible(failing);
            button.setEnabled(runnable);
            button.setToolTipText(runnable ? "Re-run failing tests for this class" : null);
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
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(false);
            setMargin(new Insets(0, 0, 0, 0));
            int size = JBUI.scale(20);
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setToolTipText("Run all failing tests");
            addActionListener(e -> {
                // Gather failing rows and re-run them
                var model = table.getModel();
                if (!(model instanceof ReportTableModel reportModel)) return;
                java.util.List<ClassTestReportRow> failing =
                        reportModel.getFilteredRows()
                                .stream()
                                .filter(r -> hasFailure(r) && r.testFqn() != null)
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
        private final JLabel missing = new JLabel("—  (missing)", SwingConstants.CENTER);

        TestRenderer() {
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setOpaque(false);
            nameLabel.setBorder(JBUI.Borders.empty(0, 6, 0, 6));
            pkgLabel.setBorder(JBUI.Borders.empty(0, 6, 0, 6));
            panel.add(inner, BorderLayout.WEST);

            missing.setForeground(new JBColor(new Color(200, 50, 50), new Color(255, 120, 120)));
            missing.setFont(missing.getFont().deriveFont(Font.ITALIC));
            missing.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            if (r.isMissingTestClass()) {
                missing.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
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
        private final JProgressBar bar = new JProgressBar(0, 100);

        CoverageRenderer() {
            bar.setBorder(JBUI.Borders.empty(2, 6));
            bar.setBorderPainted(false);
            bar.setStringPainted(true);
            bar.setOpaque(true);
            bar.setFont(UIUtil.getLabelFont());
            panel.add(bar, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;

            Color bg = isSelected
                    ? table.getSelectionBackground()
                    : new JBColor(new Color(240, 245, 250), UIUtil.getTableBackground());
            panel.setBackground(bg);

            int total = r.totalPublicMethods();
            int cov = r.coveredPublicMethods();
            String text = cov + " / " + total;

            bar.setString(text);
            bar.setForeground(new JBColor(new Color(34, 139, 34), new Color(120, 200, 120))); // fill
            bar.setBackground(new JBColor(new Color(220, 226, 232), UIUtil.getPanelBackground())); // track

            int pct = total <= 0 ? 100 : (int) Math.round((cov * 100.0) / total);
            bar.setValue(Math.max(0, Math.min(100, pct)));

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

    private static boolean hasFailure(ClassTestReportRow r) {
        if (r == null) return false;
        boolean missingTestClass = r.testFqn() == null;
        boolean coverageGap = r.totalPublicMethods() > r.coveredPublicMethods();
        boolean executionFail = r.lastFailureCount() > 0;
        return missingTestClass || coverageGap || executionFail;
    }
}
