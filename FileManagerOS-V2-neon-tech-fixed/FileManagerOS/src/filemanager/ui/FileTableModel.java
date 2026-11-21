package filemanager.ui;

import filemanager.core.FileEntry;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Table model backing the main file list. Folders are always sorted ahead
 * of files; within each group, sorting follows whichever column the user
 * last clicked.
 */
public class FileTableModel extends AbstractTableModel {

    public static final String[] COLUMNS = { "Name", "Size", "Type", "Date Modified" };

    private List<FileEntry> entries = new ArrayList<>();
    private int sortColumn = 0;
    private boolean ascending = true;

    public void setEntries(List<FileEntry> newEntries) {
        this.entries = new ArrayList<>(newEntries);
        sort();
        fireTableDataChanged();
    }

    public FileEntry getEntryAt(int row) {
        if (row < 0 || row >= entries.size()) return null;
        return entries.get(row);
    }

    public void sortBy(int column) {
        if (sortColumn == column) {
            ascending = !ascending;
        } else {
            sortColumn = column;
            ascending = true;
        }
        sort();
        fireTableDataChanged();
    }

    public int getSortColumn() { return sortColumn; }
    public boolean isAscending() { return ascending; }

    private void sort() {
        Comparator<FileEntry> cmp;
        switch (sortColumn) {
            case 1: cmp = Comparator.comparingLong(FileEntry::getSizeBytes); break;
            case 2: cmp = Comparator.comparing(FileEntry::getTypeLabel, String.CASE_INSENSITIVE_ORDER); break;
            case 3: cmp = Comparator.comparingLong(FileEntry::getLastModifiedMillis); break;
            default: cmp = Comparator.comparing(FileEntry::getName, String.CASE_INSENSITIVE_ORDER); break;
        }
        if (!ascending) cmp = cmp.reversed();
        // Folders always float to the top, for every column (including Name)
        // and regardless of sort direction — matches the README's documented
        // "Folders always stay grouped above files" behavior.
        Comparator<FileEntry> folderFirst = Comparator.comparing(e -> !e.isDirectory());
        entries.sort(folderFirst.thenComparing(cmp));
    }

    @Override public int getRowCount() { return entries.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }
    @Override public boolean isCellEditable(int row, int col) { return false; }

    @Override
    public Object getValueAt(int row, int col) {
        FileEntry e = entries.get(row);
        switch (col) {
            case 0: return e.getName();
            case 1: return e.getFormattedSize();
            case 2: return e.getTypeLabel();
            case 3: return e.getFormattedDate();
            default: return "";
        }
    }
}
