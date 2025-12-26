package com.github.skrcode.javaautounittests.view.report;


import com.github.skrcode.javaautounittests.dto.ClassTestReportRow;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import java.util.stream.Collectors;

public final class ReportTableModel extends AbstractTableModel {
    public enum Col {
        RUN(""),
        CLASS("Class"),
        TEST_CLASS("Test Class"),
        COVERAGE("Public Methods Tested"),
        FAILURES("Test Success Rate");

        public final String title;
        Col(String t) { this.title = t; }
    }

    private List<ClassTestReportRow> allRows = Collections.emptyList();
    private List<ClassTestReportRow> filteredRows = Collections.emptyList();
    private String filterText = "";

    public void setRows(List<ClassTestReportRow> rows) {
        this.allRows = rows == null ? Collections.emptyList() : new ArrayList<>(rows);
        applyFilter();
        fireTableDataChanged();
    }

    public void setFilterText(String text) {
        this.filterText = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        applyFilter();
        fireTableDataChanged();
    }

    public List<ClassTestReportRow> getFilteredRows() {
        return filteredRows;
    }


    private void applyFilter() {
        if (filterText.isBlank()) {
            filteredRows = allRows;
            return;
        }
        filteredRows = allRows.stream().filter(r -> {
            String a = safe(r.cutFqn());
            String b = safe(r.testFqn());
            String c = safe(r.cutPackageName());
            return a.contains(filterText) || b.contains(filterText) || c.contains(filterText);
        }).collect(Collectors.toList());
    }

    private static String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @Override
    public int getRowCount() { return filteredRows.size(); }

    @Override
    public int getColumnCount() { return Col.values().length; }

    @Override
    public String getColumnName(int column) { return Col.values()[column].title; }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return Object.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ClassTestReportRow r = filteredRows.get(rowIndex);
        Col c = Col.values()[columnIndex];
        return switch (c) {
            case RUN -> r;
            case CLASS -> r;
            case TEST_CLASS -> r;
            case COVERAGE -> r;
            case FAILURES -> r;
        };
    }

    public ClassTestReportRow getRowAt(int viewRow) {
        if (viewRow < 0 || viewRow >= filteredRows.size()) return null;
        return filteredRows.get(viewRow);
    }

    public List<ClassTestReportRow> getRowsAt(int[] viewRows) {
        if (viewRows == null || viewRows.length == 0) return Collections.emptyList();
        List<ClassTestReportRow> out = new ArrayList<>();
        for (int vr : viewRows) {
            ClassTestReportRow r = getRowAt(vr);
            if (r != null) out.add(r);
        }
        return out;
    }
}
