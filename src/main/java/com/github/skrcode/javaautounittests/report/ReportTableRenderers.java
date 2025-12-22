package com.github.skrcode.javaautounittests.report;

import com.intellij.icons.AllIcons;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public final class ReportTableRenderers {

    public static void install(JBTable table) {
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(40));
        table.getColumnModel().getColumn(0).setCellRenderer(new StatusRenderer());

        table.getColumnModel().getColumn(1).setCellRenderer(new CutRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new TestRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new CoverageRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new TestStatusRenderer());
    }

    private static final class StatusRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;

            JLabel label = new JLabel();
            label.setOpaque(true);
            label.setHorizontalAlignment(SwingConstants.CENTER);

            Icon icon;
            if (r.isMissingTestClass()) {
                icon = AllIcons.General.Error;
            } else if (r.getLastTestStatus() == TestRunStatus.FAIL) {
                icon = AllIcons.General.Error;
            } else {
                double cov = r.coverageRatio();
                if (cov >= 0.8) icon = AllIcons.General.InspectionsOK;
                else if (cov >= 0.5) icon = AllIcons.General.Warning;
                else icon = AllIcons.General.Error;
            }

            label.setIcon(icon);
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return label;
        }
    }

    private static final class CutRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            SimpleColoredComponent c = new SimpleColoredComponent();
            c.setOpaque(true);
            c.append(r.getCutSimpleName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            c.append("  " + r.getCutPackageName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
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
                c.append(r.getTestSimpleName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
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

            int total = r.getTotalPublicMethods();
            int cov = r.getCoveredPublicMethods();
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

    private static final class TestStatusRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                      boolean hasFocus, int row, int column) {
            ClassTestReportRow r = (ClassTestReportRow) value;
            SimpleColoredComponent c = new SimpleColoredComponent();
            c.setOpaque(true);

            switch (r.getLastTestStatus()) {
                case PASS -> {
                    c.setIcon(AllIcons.General.InspectionsOK);
                    c.append("PASS", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                }
                case FAIL -> {
                    c.setIcon(AllIcons.General.Error);
                    c.append("FAIL", SimpleTextAttributes.ERROR_ATTRIBUTES);
                    c.append("  (" + r.getLastFailureCount() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                case RUNNING -> {
                    c.setIcon(AllIcons.Process.Step_1);
                    c.append("RUNNING", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
                case NEVER_RUN -> {
                    c.setIcon(AllIcons.General.Information);
                    c.append("NEVER RUN", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }

            c.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return c;
        }
    }
}
