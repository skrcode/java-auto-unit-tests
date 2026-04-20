package com.github.skrcode.javaautounittests.view.report;

import com.github.skrcode.javaautounittests.dto.ClassTestReportRow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ReportTableRenderers {

    public interface InlineFixDelegate {
        void requestFix(@NotNull ClassTestReportRow row);

        void requestCancel(@NotNull ClassTestReportRow row);

        void toggleDetails(@NotNull ClassTestReportRow row);

        @Nullable InlineFixRowState getInlineState(@NotNull ClassTestReportRow row);

        int getAnimationFrame();
    }

    public static void install(
            @NotNull JBTable table,
            @NotNull Project project,
            @NotNull InlineFixDelegate delegate
    ) {
        int runCol = ReportTableModel.Col.RUN.ordinal();
        int classCol = ReportTableModel.Col.CLASS.ordinal();
        int testCol = ReportTableModel.Col.TEST_CLASS.ordinal();
        int coverageCol = ReportTableModel.Col.COVERAGE.ordinal();
        int failureCol = ReportTableModel.Col.FAILURES.ordinal();
        int statusCol = ReportTableModel.Col.STATUS.ordinal();

        table.getColumnModel().getColumn(runCol).setMaxWidth(JBUI.scale(88));
        table.getColumnModel().getColumn(runCol).setMinWidth(JBUI.scale(58));

        RunAgainCell runCell = new RunAgainCell(project, table, delegate);
        table.getColumnModel().getColumn(runCol).setCellRenderer(runCell);
        table.getColumnModel().getColumn(runCol).setCellEditor(runCell);

        table.getColumnModel().getColumn(classCol).setCellRenderer(new CutRenderer());
        table.getColumnModel().getColumn(testCol).setCellRenderer(new TestRenderer());
        table.getColumnModel().getColumn(coverageCol).setCellRenderer(new CoverageRenderer());
        table.getColumnModel().getColumn(failureCol).setCellRenderer(new FailureRenderer());
        table.getColumnModel().getColumn(statusCol).setCellRenderer(new LiveStatusRenderer(delegate));

        table.getColumnModel().getColumn(statusCol).setPreferredWidth(JBUI.scale(320));
        table.getColumnModel().getColumn(coverageCol).setHeaderRenderer(new RightAlignedHeader(table));
        table.getColumnModel().getColumn(failureCol).setHeaderRenderer(new RightAlignedHeader(table));
    }

    private static final class RunAgainCell extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {
        private final JButton actionButton = new JButton();
        private final JLabel statusIcon = new JLabel("", SwingConstants.CENTER);
        private final JPanel empty = new JPanel();
        private final JBTable table;
        private final InlineFixDelegate delegate;
        private ClassTestReportRow currentRow;

        RunAgainCell(Project project, JBTable table, InlineFixDelegate delegate) {
            this.table = table;
            this.delegate = delegate;

            actionButton.setOpaque(false);
            actionButton.setContentAreaFilled(false);
            actionButton.setBorderPainted(false);
            actionButton.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            actionButton.setFocusPainted(false);
            actionButton.setRolloverEnabled(false);
            actionButton.setMargin(new Insets(0, 0, 0, 0));
            actionButton.addActionListener(this);

            statusIcon.setOpaque(false);

            int size = JBUI.scale(22);
            Dimension d = new Dimension(size, size);
            actionButton.setPreferredSize(d);
            actionButton.setMinimumSize(d);
            actionButton.setMaximumSize(d);
            empty.setOpaque(false);
            empty.setPreferredSize(d);
            empty.setMinimumSize(d);
            empty.setMaximumSize(d);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentRow == null) return;
            InlineFixRowState state = delegate.getInlineState(currentRow);
            if (state != null && state.canCancel()) {
                delegate.requestCancel(currentRow);
            } else {
                delegate.requestFix(currentRow);
            }
            fireEditingStopped();
        }

        private Component rendererComponent(JTable table, Object value, boolean isSelected) {
            ClassTestReportRow rowData = (ClassTestReportRow) value;
            InlineFixRowState state = delegate.getInlineState(rowData);

            if (state != null && state.canCancel()) {
                actionButton.setText("Stop");
                actionButton.setIcon(AllIcons.Actions.Suspend);
                actionButton.setToolTipText("Cancel running fix job");
                actionButton.setEnabled(true);
                actionButton.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return actionButton;
            }

            if (state != null && (state.status() == InlineFixRowState.Status.SUCCESS
                    || state.status() == InlineFixRowState.Status.FAILED
                    || state.status() == InlineFixRowState.Status.CANCELLED)) {
                statusIcon.setIcon(iconForState(state.status()));
                statusIcon.setToolTipText(state.stage() + ": " + state.latestMessage());
                statusIcon.setForeground(foregroundForState(state.status()));
                return statusIcon;
            }

            boolean failing = rowData != null && rowData.hasFailures();
            if (!failing) {
                empty.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return empty;
            }

            boolean runnable = rowData.cutPsi() != null;
            actionButton.setText("Fix");
            actionButton.setIcon(AllIcons.Actions.Execute);
            actionButton.setEnabled(runnable);
            actionButton.setToolTipText(runnable ? "Generate tests for this class" : null);
            actionButton.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return actionButton;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return rendererComponent(table, value, isSelected);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            int modelRow = this.table.convertRowIndexToModel(row);
            Object val = this.table.getModel().getValueAt(modelRow, column);
            currentRow = (ClassTestReportRow) val;
            return rendererComponent(table, val, true);
        }

        @Override
        public boolean isCellEditable(java.util.EventObject e) {
            if (!(e instanceof java.awt.event.MouseEvent me)) {
                return false;
            }
            int viewRow = table.rowAtPoint(me.getPoint());
            int viewCol = table.columnAtPoint(me.getPoint());
            if (viewRow < 0 || viewCol != ReportTableModel.Col.RUN.ordinal()) return false;

            ClassTestReportRow rowData = tableModelRow(viewRow);
            if (rowData == null) return false;
            InlineFixRowState state = delegate.getInlineState(rowData);
            if (state != null && state.canCancel()) return true;

            return rowData.hasFailures() && rowData.cutPsi() != null;
        }

        private ClassTestReportRow tableModelRow(int viewRow) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            Object val = table.getModel().getValueAt(modelRow, ReportTableModel.Col.RUN.ordinal());
            return (val instanceof ClassTestReportRow rowData) ? rowData : null;
        }

        @Override
        public Object getCellEditorValue() {
            return currentRow;
        }
    }

    private static Icon iconForState(InlineFixRowState.Status status) {
        return switch (status) {
            case SUCCESS -> AllIcons.General.InspectionsOK;
            case FAILED -> AllIcons.General.Error;
            case CANCELLED -> AllIcons.Actions.Suspend;
            default -> AllIcons.General.Information;
        };
    }

    private static Color foregroundForState(InlineFixRowState.Status status) {
        return switch (status) {
            case SUCCESS -> new JBColor(new Color(25, 130, 70), new Color(170, 230, 190));
            case FAILED -> new JBColor(new Color(170, 45, 45), new Color(255, 170, 170));
            case CANCELLED -> new JBColor(new Color(130, 95, 30), new Color(240, 210, 150));
            default -> UIUtil.getLabelForeground();
        };
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
            nameLabel.setFont(nameLabel.getFont());
            nameLabel.setForeground(UIUtil.getLabelForeground());

            pkgLabel.setText(wrapPackage(r.cutPackageName()));
            pkgLabel.setForeground(JBColor.GRAY);
            pkgLabel.setFont(pkgLabel.getFont());

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
            nameLabel.setFont(nameLabel.getFont());
            nameLabel.setForeground(UIUtil.getLabelForeground());

            String pkg = r.testFqn() == null ? "" : pkgPart(r.testFqn());
            pkgLabel.setText(wrapPackage(pkg));
            pkgLabel.setForeground(JBColor.GRAY);
            pkgLabel.setFont(pkgLabel.getFont());

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

            if (r.coverageStatus() == ClassTestReportRow.CoverageStatus.NOT_ANALYZED) {
                label.setText("Not analyzed");
                label.setToolTipText("Click Analyze Coverage to compute method coverage");
                return panel;
            }

            int total = r.totalPublicMethods();
            int cov = r.coveredPublicMethods();
            int pct = total <= 0 ? 0 : (int) Math.round((cov * 100.0) / total);
            pct = Math.max(0, Math.min(100, pct));
            label.setText(cov + " / " + total + " (" + pct + "%)");
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

            int successPct = r.lastFailureCount() > 0 ? 0 : 100;
            label.setText(successPct + "%");
            label.setToolTipText("Assumed from last run result");
            return panel;
        }
    }

    private static final class LiveStatusRenderer implements TableCellRenderer {
        private static final String[] SPINNER_FRAMES = {"◴", "◷", "◶", "◵"};
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JLabel label = new JLabel("", SwingConstants.LEFT);
        private final InlineFixDelegate delegate;

        LiveStatusRenderer(InlineFixDelegate delegate) {
            this.delegate = delegate;
            label.setBorder(JBUI.Borders.empty(2, 8, 2, 8));
            panel.add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            ClassTestReportRow data = (ClassTestReportRow) value;
            InlineFixRowState state = delegate.getInlineState(data);

            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            if (state == null) {
                label.setText("—");
                label.setForeground(JBColor.GRAY);
                label.setToolTipText("No inline run started");
                return panel;
            }

            String detailsToggle = state.detailLines().isEmpty()
                    ? ""
                    : "<br/><span style='color:#7a7a7a;'>" + (state.expanded() ? "▴ Hide details" : "▾ Show details") + "</span>";

            StringBuilder details = new StringBuilder();
            if (state.expanded() && !state.detailLines().isEmpty()) {
                details.append("<br/>");
                for (String line : state.detailLines()) {
                    details.append("<span style='color:#666666;'>• ")
                            .append(escape(line))
                            .append("</span><br/>");
                }
            }

            String progress = state.progressPercent() > 0 ? state.progressPercent() + "%" : "—";
            String elapsed = state.elapsedMs() > 0 ? formatElapsed(state.elapsedMs()) : "0s";
            String stagePrefix = "";
            if (state.status() == InlineFixRowState.Status.RUNNING) {
                stagePrefix = SPINNER_FRAMES[Math.floorMod(delegate.getAnimationFrame(), SPINNER_FRAMES.length)] + " ";
            } else if (state.status() == InlineFixRowState.Status.QUEUED) {
                stagePrefix = "⋯ ";
            }
            label.setText("<html><b>"
                    + escape(stagePrefix + state.stage())
                    + "</b> · "
                    + escape(state.latestMessage())
                    + " <span style='color:#8a8a8a;'>("
                    + progress
                    + ", "
                    + elapsed
                    + ")</span>"
                    + detailsToggle
                    + details
                    + "</html>");
            label.setForeground(foregroundForState(state.status()));
            label.setToolTipText(state.stage() + ": " + state.latestMessage());
            return panel;
        }
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String formatElapsed(long elapsedMs) {
        if (elapsedMs < 1000) return elapsedMs + "ms";
        long seconds = elapsedMs / 1000;
        long millis = elapsedMs % 1000;
        return seconds + "." + (millis / 100) + "s";
    }

    private static String wrapPackage(String pkg) {
        if (pkg == null) return "";
        String escaped = escape(pkg);
        return "<html><span style='color:#666666; font-size:smaller;'>"
                + escaped.replace(".", ".&#8203;")
                + "</span></html>";
    }

    private static String pkgPart(String fqn) {
        if (fqn == null) return "";
        int idx = fqn.lastIndexOf('.');
        return idx > 0 ? fqn.substring(0, idx) : "";
    }
}
