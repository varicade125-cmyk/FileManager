package filemanager.ui;

import filemanager.core.FileEntry;
import filemanager.core.FileOperations;
import filemanager.core.ShortcutResolver;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The central panel: a dark, minimalist table listing the contents of the
 * current directory. Handles selection, double-click navigation/opening,
 * a right-click context menu, keyboard shortcuts (Delete, F2, Ctrl+C/X/V),
 * and drag-and-drop both out to the OS (e.g. dragging a file to the desktop)
 * and in from it (dragging files from the OS into the app).
 */
public class FileTablePanel extends JPanel {

    /** Callback surface the panel uses to ask the outside world to do things. */
    public interface Listener {
        void onNavigateInto(File folder);
        void onOpenFile(File file);
        void onOpenUrl(String url);
        void onRequestRename(File file);
        void onRequestDelete(List<File> files, boolean permanent);
        void onRequestCopy(List<File> files);
        void onRequestCut(List<File> files);
        void onRequestPaste();
        void onRequestNewFolder();
        void onRequestNewFile();
        void onRequestProperties(List<File> files);
        void onSelectionChanged(int count, long totalBytes);
        void onRequestRefresh();
        /** move is true when the drop should move the files (the user's OS
         *  modifier-key/drag convention requested a move), false to copy. */
        void onFilesDropped(List<File> files, File destinationFolder, boolean move);
        void onUndo();
    }

    private final JTable table;
    private final FileTableModel model;
    private final Listener listener;
    private File currentDirectory;

    public FileTablePanel(Listener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setBackground(Theme.BG_DARKEST);

        model = new FileTableModel();
        table = new JTable(model);
        configureTable();
        installDragAndDrop();

        JScrollPane scroll = new JScrollPane(table);
        Theme.styleScrollPane(scroll, Theme.BG_DARKEST);
        add(scroll, BorderLayout.CENTER);

        installShortcuts();
    }

    /** Lets the panel know which folder it's currently displaying, so drag-and-drop
     *  can tell an internal move (drag within this listing) from an external copy
     *  (drag in from the OS) and pick a sensible default destination for drops
     *  that don't land on a specific folder row. */
    public void setCurrentDirectory(File dir) {
        this.currentDirectory = dir;
    }

    private void configureTable() {
        table.setBackground(Theme.BG_DARKEST);
        table.setForeground(Theme.TEXT_PRIMARY);
        table.setSelectionBackground(Theme.BG_SELECTED);
        table.setSelectionForeground(Theme.TEXT_PRIMARY);
        table.setGridColor(Theme.BG_DARKEST);
        table.setShowGrid(false);
        table.setRowHeight(28);
        table.setFont(Theme.FONT_UI);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        JTableHeader header = table.getTableHeader();
        header.setBackground(Theme.BG_PANEL);
        header.setForeground(Theme.TEXT_SECONDARY);
        header.setFont(Theme.FONT_UI_BOLD);
        header.setPreferredSize(new Dimension(0, 30));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col >= 0) {
                    model.sortBy(col);
                }
            }
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(340);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);

        table.getColumnModel().getColumn(0).setCellRenderer(new NameRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new PlainRenderer(SwingConstants.RIGHT));
        table.getColumnModel().getColumn(2).setCellRenderer(new PlainRenderer(SwingConstants.LEFT));
        table.getColumnModel().getColumn(3).setCellRenderer(new PlainRenderer(SwingConstants.LEFT));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) fireSelectionChanged();
        });

        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                maybeAdjustSelection(e);
                // On Linux/macOS the popup trigger fires on mouse *press*, not release
                // (Windows is the reverse), so the menu has to be handled here too.
                // maybePopup() only acts when isPopupTrigger() is true, so on Windows
                // (where the press is never a trigger) this is a no-op.
                maybePopup(e);
            }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) openRow(row);
                }
            }
        });
    }

    /** Set by importData() whenever a drop lands on this same table, so
     *  exportDone() (fired for the same drag) knows the file operation was
     *  already carried out via onFilesDropped()/FileOperations and doesn't
     *  need to (and shouldn't try to) delete the originals itself. Reset at
     *  the start of every new drag. */
    private boolean dropHandledLocally = false;

    /**
     * Wires up drag-and-drop in both directions on the table:
     *  - Export: dragging selected rows out of the app (e.g. onto the OS
     *    desktop or into another application). If the drop target reports
     *    the action as MOVE, the dragged-and-dropped-standard contract makes
     *    *this* side responsible for deleting the originals afterward.
     *  - Import: dropping files onto the table, either from the OS (an
     *    external file/folder) or from within this same listing (dragging
     *    an item onto a folder row to move it in).
     */
    private void installDragAndDrop() {
        table.setDragEnabled(true);
        table.setDropMode(DropMode.ON);
        table.setTransferHandler(new TransferHandler() {

            @Override
            public int getSourceActions(JComponent c) {
                return TransferHandler.COPY_OR_MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                dropHandledLocally = false;
                List<File> files = getSelectedFiles();
                return files.isEmpty() ? null : new FileListTransferable(files);
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void exportDone(JComponent source, Transferable data, int action) {
                // A drop that landed back on this table was already carried out
                // by importData()/onFilesDropped() (via moveInto/copyInto) —
                // deleting the "originals" again here would either be a no-op
                // (already gone) or, worse, mask a real second failure since
                // both are swallowed the same way. Only an external drop (onto
                // the OS desktop or another application) needs this class to
                // clean up the source itself.
                if (dropHandledLocally) return;
                if (action != TransferHandler.MOVE) return;
                try {
                    List<File> files = (List<File>) data.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        FileOperations.delete(f);
                    }
                    listener.onRequestRefresh();
                } catch (Exception ignored) {
                    // If we can't confirm the target actually has the data, better to
                    // leave the originals in place than risk deleting them for nothing.
                }
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<File> dropped = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (dropped.isEmpty()) return false;

                    File destination = currentDirectory;
                    if (support.isDrop() && support.getDropLocation() instanceof JTable.DropLocation) {
                        JTable.DropLocation loc = (JTable.DropLocation) support.getDropLocation();
                        FileEntry rowEntry = loc.getRow() >= 0 ? model.getEntryAt(loc.getRow()) : null;
                        if (rowEntry != null && rowEntry.isDirectory()) {
                            destination = rowEntry.getFile();
                        }
                    }
                    if (destination == null) return false;

                    // Honor the action the user actually requested (via the OS's
                    // usual drag modifier-key convention) instead of guessing
                    // move-vs-copy from where the files came from.
                    boolean move = support.isDrop() && support.getDropAction() == TransferHandler.MOVE;
                    dropHandledLocally = true;
                    listener.onFilesDropped(dropped, destination, move);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }

    /** Minimal Transferable exposing a list of local files — the standard
     *  contract both other Swing components and native OS drop targets
     *  (Explorer, Finder, file managers, etc.) understand. */
    private static class FileListTransferable implements Transferable {
        private final List<File> files;
        FileListTransferable(List<File> files) { this.files = files; }

        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.javaFileListFlavor };
        }
        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }
        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return files;
        }
    }

    private void maybeAdjustSelection(MouseEvent e) {
        if (e.isPopupTrigger()) return;
        int row = table.rowAtPoint(e.getPoint());
        if (row >= 0 && !table.isRowSelected(row) && !e.isShiftDown() && !e.isControlDown()) {
            table.setRowSelectionInterval(row, row);
        } else if (row < 0 && SwingUtilities.isLeftMouseButton(e)) {
            table.clearSelection();
        }
        if (e.isPopupTrigger()) showContextMenu(e);
    }

    private void maybePopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
            int row = table.rowAtPoint(e.getPoint());
            if (row >= 0 && !table.isRowSelected(row)) {
                table.setRowSelectionInterval(row, row);
            }
            showContextMenu(e);
        }
    }

    private void openRow(int row) {
        FileEntry entry = model.getEntryAt(row);
        if (entry == null) return;
        File file = entry.getFile();

        // Web-link style shortcuts (.url files, or .desktop "Type=Link" entries)
        // open in the default browser instead of being treated as local files.
        if (entry.isInternetShortcut() || entry.isDesktopEntry()) {
            String url = ShortcutResolver.resolveUrlTarget(file);
            if (url != null) {
                listener.onOpenUrl(url);
                return;
            }
        }

        // Windows .lnk shortcuts and .desktop links that point to a local
        // file/folder: resolve the real target so double-clicking a shortcut
        // behaves like double-clicking what it points to, not the shortcut
        // file itself.
        if (entry.isWindowsShortcut() || entry.isDesktopEntry()) {
            File target = ShortcutResolver.resolveFileTarget(file);
            if (target != null && target.isDirectory()) {
                listener.onNavigateInto(target);
                return;
            }
            if (target != null && target.isFile()) {
                listener.onOpenFile(target);
                return;
            }
            // Couldn't confidently resolve it ourselves (e.g. a shortcut to an
            // application, or a non-standard .lnk) — hand the shortcut file
            // itself to the OS and let it interpret it, exactly like a native
            // double-click would.
            listener.onOpenFile(file);
            return;
        }

        // Plain files/folders, and symbolic links (java.io.File already
        // resolves isDirectory()/isFile() through a symlink transparently).
        if (entry.isDirectory()) {
            listener.onNavigateInto(file);
        } else {
            listener.onOpenFile(file);
        }
    }

    private void installShortcuts() {
        InputMap im = table.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = table.getActionMap();

        bind(im, am, "DELETE", "delete", () -> listener.onRequestDelete(getSelectedFiles(), false));
        bind(im, am, "shift DELETE", "deletePermanently", () -> listener.onRequestDelete(getSelectedFiles(), true));
        bind(im, am, "F2", "rename", () -> {
            List<File> sel = getSelectedFiles();
            if (sel.size() == 1) listener.onRequestRename(sel.get(0));
        });
        bind(im, am, "control C", "copy", () -> listener.onRequestCopy(getSelectedFiles()));
        bind(im, am, "control X", "cut", () -> listener.onRequestCut(getSelectedFiles()));
        bind(im, am, "control V", "paste", listener::onRequestPaste);
        bind(im, am, "control Z", "undo", listener::onUndo);
        bind(im, am, "ENTER", "open", () -> {
            int row = table.getSelectedRow();
            if (row >= 0) openRow(row);
        });
    }

    private void bind(InputMap im, ActionMap am, String keystroke, String name, Runnable action) {
        im.put(KeyStroke.getKeyStroke(keystroke), name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void showContextMenu(MouseEvent e) {
        List<File> selected = getSelectedFiles();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Theme.BG_PANEL);
        menu.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        if (!selected.isEmpty()) {
            addItem(menu, "Open", () -> openRow(table.getSelectedRow()));
            menu.addSeparator();
            addItem(menu, "Cut", () -> listener.onRequestCut(selected));
            addItem(menu, "Copy", () -> listener.onRequestCopy(selected));
        }
        addItem(menu, "Paste", listener::onRequestPaste);
        menu.addSeparator();
        if (selected.size() == 1) {
            addItem(menu, "Rename", () -> listener.onRequestRename(selected.get(0)));
        }
        if (!selected.isEmpty()) {
            addItem(menu, "Delete", () -> listener.onRequestDelete(selected, false));
            addItem(menu, "Delete Permanently   (Shift+Del)", Theme.DANGER, () -> listener.onRequestDelete(selected, true));
        }
        menu.addSeparator();
        addItem(menu, "New Folder", listener::onRequestNewFolder);
        addItem(menu, "New File", listener::onRequestNewFile);
        if (!selected.isEmpty()) {
            menu.addSeparator();
            addItem(menu, "Properties", () -> listener.onRequestProperties(selected));
        }

        menu.show(table, e.getX(), e.getY());
    }

    private void addItem(JPopupMenu menu, String text, Runnable action) {
        addItem(menu, text, Theme.TEXT_PRIMARY, action);
    }

    private void addItem(JPopupMenu menu, String text, Color color, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(Theme.BG_PANEL);
        item.setForeground(color);
        item.setFont(Theme.FONT_UI);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void fireSelectionChanged() {
        // Look each selected row's entry up directly. (This used to rescan the whole
        // table once per selected file, which made Select-All on a big folder O(n^2)
        // and froze the UI for seconds.)
        long total = 0L;
        int count = 0;
        for (int row : table.getSelectedRows()) {
            FileEntry entry = model.getEntryAt(row);
            if (entry != null) {
                total += entry.getSizeBytes();
                count++;
            }
        }
        listener.onSelectionChanged(count, total);
    }

    public List<File> getSelectedFiles() {
        List<File> files = new ArrayList<>();
        for (int row : table.getSelectedRows()) {
            FileEntry entry = model.getEntryAt(row);
            if (entry != null) files.add(entry.getFile());
        }
        return files;
    }

    public void setEntries(List<FileEntry> entries) {
        model.setEntries(entries);
        fireSelectionChanged();
    }

    public void selectByName(String name) {
        for (int i = 0; i < model.getRowCount(); i++) {
            FileEntry entry = model.getEntryAt(i);
            if (entry != null && entry.getName().equals(name)) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    public int getRowCount() { return model.getRowCount(); }

    public void requestTableFocus() { table.requestFocusInWindow(); }

    // ---- Renderers ----------------------------------------------------

    private class NameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            FileEntry entry = model.getEntryAt(row);
            label.setBorder(new EmptyBorder(0, 10, 0, 0));
            label.setIconTextGap(10);
            if (entry != null) {
                label.setIcon(IconProvider.iconFor(entry));
                label.setForeground(entry.isHidden() ? Theme.TEXT_SECONDARY : Theme.TEXT_PRIMARY);
            }
            paintRow(label, isSelected, row);
            return label;
        }
    }

    private class PlainRenderer extends DefaultTableCellRenderer {
        PlainRenderer(int align) { setHorizontalAlignment(align); }
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int col) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            label.setForeground(Theme.TEXT_SECONDARY);
            label.setBorder(new EmptyBorder(0, 4, 0, 10));
            paintRow(label, isSelected, row);
            return label;
        }
    }

    private void paintRow(JLabel label, boolean isSelected, int row) {
        if (isSelected) {
            label.setBackground(Theme.BG_SELECTED);
        } else {
            label.setBackground(row % 2 == 0 ? Theme.BG_DARKEST : Theme.BG_ROW_ALT);
        }
        label.setOpaque(true);
    }

    private static class HeaderRenderer implements TableCellRenderer {
        private final DefaultTableCellRenderer base = new DefaultTableCellRenderer();
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int col) {
            JLabel label = (JLabel) base.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            label.setBackground(Theme.BG_PANEL);
            label.setForeground(Theme.TEXT_SECONDARY);
            label.setFont(Theme.FONT_UI_BOLD);
            label.setBorder(new EmptyBorder(0, col == 0 ? 12 : 4, 0, 4));
            label.setOpaque(true);
            return label;
        }
    }
}
