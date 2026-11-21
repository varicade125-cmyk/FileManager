package filemanager.ui;

import filemanager.core.Clipboard;
import filemanager.core.DeleteHistory;
import filemanager.core.FileEntry;
import filemanager.core.FileOperations;
import filemanager.core.FileSearchService;
import filemanager.core.TrashManager;
import filemanager.core.TrashRecord;
import filemanager.dialogs.InputDialog;
import filemanager.dialogs.PropertiesDialog;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The application window. Owns navigation history, the clipboard, and wires
 * the Toolbar / SidebarPanel / FileTablePanel / StatusBar together. This is
 * the only class that decides *what* happens; the sub-panels only report
 * user intent through their Listener interfaces.
 */
public class MainFrame extends JFrame
        implements Toolbar.Listener, FileTablePanel.Listener, SidebarPanel.Listener {

    private final Toolbar toolbar;
    private final SidebarPanel sidebar;
    private final FileTablePanel filePanel;
    private final StatusBar statusBar;

    private final Deque<File> backStack = new ArrayDeque<>();
    private final Deque<File> forwardStack = new ArrayDeque<>();
    private final Clipboard clipboard = new Clipboard();
    private final DeleteHistory deleteHistory = new DeleteHistory();

    private File currentDir;
    private boolean showHidden = false;
    private SwingWorker<List<FileEntry>, Void> activeSearch;

    public MainFrame() {
        super("File Manager");
        getContentPane().setBackground(Theme.BG_DARKEST);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1080, 680);
        setMinimumSize(new Dimension(760, 480));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        toolbar = new Toolbar(this);
        sidebar = new SidebarPanel(this);
        filePanel = new FileTablePanel(this);
        statusBar = new StatusBar();

        add(toolbar, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(filePanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        navigateTo(new File(System.getProperty("user.home")), true);
        SwingUtilities.invokeLater(filePanel::requestTableFocus);
    }

    // ---- Navigation -----------------------------------------------------

    /** Returns true if the folder was actually opened, false if it couldn't be (an error
     *  dialog has already been shown in that case). */
    private boolean navigateTo(File dir, boolean pushHistory) {
        if (dir == null || !dir.isDirectory()) {
            showError("Cannot open folder", "That location no longer exists or isn't a folder.");
            return false;
        }
        // A search still running in the background must not be allowed to
        // land its results on top of whatever folder we're navigating to —
        // its done() callback would otherwise overwrite the new view with
        // stale results a moment after this method returns.
        if (activeSearch != null && !activeSearch.isDone()) {
            activeSearch.cancel(true);
        }
        toolbar.setSearching(false);
        try {
            List<FileEntry> entries = FileOperations.listDirectory(dir, showHidden);
            if (pushHistory && currentDir != null && !currentDir.equals(dir)) {
                backStack.push(currentDir);
                forwardStack.clear();
            }
            currentDir = dir;
            filePanel.setCurrentDirectory(dir);
            filePanel.setEntries(entries);
            toolbar.setPath(dir);
            toolbar.clearSearch();
            toolbar.setNavEnabled(!backStack.isEmpty(), !forwardStack.isEmpty(), dir.getParentFile() != null);
            sidebar.setSelectedIfMatches(dir);
            statusBar.setItemCount(entries.size());
            statusBar.setSelection(0, 0);
            setTitle("File Manager \u2014 " + dir.getAbsolutePath());
            return true;
        } catch (IOException e) {
            showError("Cannot open folder", e.getMessage());
            return false;
        }
    }

    private void refresh() {
        if (currentDir != null) navigateTo(currentDir, false);
    }

    @Override public void onBack() {
        if (backStack.isEmpty()) return;
        File previous = currentDir;
        forwardStack.push(previous);
        if (!navigateTo(backStack.pop(), false)) {
            // That folder is gone/unreadable, so we never actually left 'previous':
            // undo the forward entry (otherwise Forward would "return" to the folder
            // we're still in). The dead entry stays dropped so Back moves on to the
            // next valid one. Button states are refreshed because the stacks changed.
            forwardStack.pop();
            toolbar.setNavEnabled(!backStack.isEmpty(), !forwardStack.isEmpty(), currentDir.getParentFile() != null);
        }
    }

    @Override public void onForward() {
        if (forwardStack.isEmpty()) return;
        File previous = currentDir;
        backStack.push(previous);
        if (!navigateTo(forwardStack.pop(), false)) {
            backStack.pop(); // same reasoning as onBack()
            toolbar.setNavEnabled(!backStack.isEmpty(), !forwardStack.isEmpty(), currentDir.getParentFile() != null);
        }
    }

    @Override public void onUp() {
        if (currentDir == null) return;
        File parent = currentDir.getParentFile();
        if (parent != null) navigateTo(parent, true);
    }

    @Override public void onRefresh() { refresh(); }

    @Override public void onNavigateToPath(String path) {
        if (path == null || path.isEmpty()) return;
        File target = new File(path);
        if (target.isDirectory()) {
            navigateTo(target, true);
        } else {
            showError("Path not found", "\"" + path + "\" isn't a folder that exists on this system.");
        }
    }

    @Override public void onLocationSelected(File location) { navigateTo(location, true); }

    @Override public void onNavigateInto(File folder) { navigateTo(folder, true); }

    @Override public void onOpenFile(File file) {
        try {
            FileOperations.openWithSystem(file);
        } catch (IOException e) {
            showError("Couldn't open file", e.getMessage());
        }
    }

    @Override public void onOpenUrl(String url) {
        try {
            FileOperations.openUrl(url);
        } catch (IOException e) {
            showError("Couldn't open link", e.getMessage());
        }
    }

    // ---- Create / rename / delete ---------------------------------------

    @Override public void onRequestNewFolder() {
        String name = InputDialog.show(this, "New Folder", "Folder name:", "New folder");
        if (name == null) return;
        try {
            File created = FileOperations.createFolder(currentDir, name);
            refresh();
            filePanel.selectByName(created.getName());
        } catch (IOException e) {
            showError("Couldn't create folder", e.getMessage());
        }
    }

    @Override public void onRequestNewFile() {
        String name = InputDialog.show(this, "New File", "File name:", "New file.txt");
        if (name == null) return;
        try {
            File created = FileOperations.createFile(currentDir, name);
            refresh();
            filePanel.selectByName(created.getName());
        } catch (IOException e) {
            showError("Couldn't create file", e.getMessage());
        }
    }

    @Override public void onRequestRename(File file) {
        String name = InputDialog.show(this, "Rename", "New name:", file.getName());
        if (name == null || name.equals(file.getName())) return;
        try {
            FileOperations.rename(file, name);
            refresh();
            filePanel.selectByName(name);
        } catch (IOException e) {
            showError("Couldn't rename", e.getMessage());
        }
    }

    @Override public void onRequestDelete(List<File> files, boolean permanent) {
        if (files.isEmpty()) return;

        if (permanent) {
            String message = files.size() == 1
                    ? "Permanently delete \"" + files.get(0).getName() + "\"? This cannot be undone."
                    : "Permanently delete these " + files.size() + " items? This cannot be undone.";
            int choice = JOptionPane.showConfirmDialog(this, message, "Delete Permanently",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;

            StringBuilder errors = new StringBuilder();
            for (File f : files) {
                try {
                    FileOperations.delete(f);
                } catch (IOException e) {
                    errors.append(f.getName()).append(": ").append(e.getMessage()).append("\n");
                }
            }
            refresh();
            if (errors.length() > 0) showError("Some items couldn't be deleted", errors.toString());
            return;
        }

        // Default action: send to the OS recycle bin / trash rather than
        // deleting outright, so it can be recovered from there if needed.
        List<File> failedToTrash = new ArrayList<>();
        List<TrashRecord> trashedBatch = new ArrayList<>();
        for (File f : files) {
            boolean isDir = f.isDirectory();
            if (FileOperations.moveToTrash(f)) {
                trashedBatch.add(new TrashRecord(f, System.currentTimeMillis(), isDir));
            } else {
                failedToTrash.add(f);
            }
        }
        deleteHistory.push(trashedBatch);
        refresh();

        if (!failedToTrash.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (File f : failedToTrash) {
                if (names.length() > 0) names.append(", ");
                names.append(f.getName());
            }
            String message = "This system doesn't support moving these items to the recycle bin:\n"
                    + names + "\n\nDelete them permanently instead?";
            int choice = JOptionPane.showConfirmDialog(this, message, "Recycle Bin Unavailable",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                StringBuilder errors = new StringBuilder();
                for (File f : failedToTrash) {
                    try {
                        FileOperations.delete(f);
                    } catch (IOException e) {
                        errors.append(f.getName()).append(": ").append(e.getMessage()).append("\n");
                    }
                }
                refresh();
                if (errors.length() > 0) showError("Some items couldn't be deleted", errors.toString());
            }
        }
    }

    @Override public void onRequestProperties(List<File> files) {
        PropertiesDialog.show(this, files);
    }

    // ---- Undo ---------------------------------------------------------------

    @Override public void onUndo() {
        if (deleteHistory.isEmpty()) return;
        List<TrashRecord> batch = deleteHistory.pop();

        List<TrashRecord> failed = new ArrayList<>();
        for (TrashRecord record : batch) {
            if (!TrashManager.restore(record)) failed.add(record);
        }
        refresh();

        int restoredCount = batch.size() - failed.size();
        if (failed.isEmpty()) {
            statusBar.setClipboardStatus(restoredCount == 1 ? "1 item restored" : restoredCount + " items restored");
        } else {
            StringBuilder names = new StringBuilder();
            for (TrashRecord record : failed) {
                if (names.length() > 0) names.append(", ");
                names.append(record.originalLocation.getName());
            }
            String partial = restoredCount > 0 ? restoredCount + " of " + batch.size() : "0 of " + batch.size();
            showError("Couldn't fully undo that delete",
                    partial + " item(s) were restored automatically.\n\n"
                    + "Couldn't confirm restoring: " + names
                    + "\n\nThis can happen if they were already emptied from the recycle bin, "
                    + "or if this OS doesn't expose a reliable way to restore items programmatically. "
                    + "Check the recycle bin/trash manually for these.");
        }
    }

    // ---- Clipboard --------------------------------------------------------

    @Override public void onRequestCopy(List<File> files) {
        if (files.isEmpty()) return;
        clipboard.setCopy(files);
        statusBar.setClipboardStatus(files.size() + (files.size() == 1 ? " item copied" : " items copied"));
    }

    @Override public void onRequestCut(List<File> files) {
        if (files.isEmpty()) return;
        clipboard.setCut(files);
        statusBar.setClipboardStatus(files.size() + (files.size() == 1 ? " item cut" : " items cut"));
    }

    @Override public void onRequestPaste() {
        if (clipboard.isEmpty() || currentDir == null) return;
        StringBuilder errors = new StringBuilder();
        File lastCreated = null;
        for (File f : clipboard.getFiles()) {
            try {
                if (!f.exists()) {
                    errors.append(f.getName()).append(": no longer exists\n");
                    continue;
                }
                if (clipboard.isCut() && f.getParentFile() != null && f.getParentFile().equals(currentDir)) {
                    continue; // moving into the folder it's already in - nothing to do
                }
                lastCreated = clipboard.isCut()
                        ? FileOperations.moveInto(f, currentDir)
                        : FileOperations.copyInto(f, currentDir);
            } catch (IOException e) {
                errors.append(f.getName()).append(": ").append(e.getMessage()).append("\n");
            }
        }
        boolean wasCut = clipboard.isCut();
        if (wasCut) {
            clipboard.clear();
            statusBar.setClipboardStatus(null);
        }
        refresh();
        if (lastCreated != null) filePanel.selectByName(lastCreated.getName());
        if (errors.length() > 0) showError("Some items couldn't be pasted", errors.toString());
    }

    // ---- Toolbar passthroughs that operate on the current selection -----

    @Override public void onNewFolder() { onRequestNewFolder(); }
    @Override public void onNewFile() { onRequestNewFile(); }
    @Override public void onDelete(boolean permanent) { onRequestDelete(filePanel.getSelectedFiles(), permanent); }
    @Override public void onRename() {
        List<File> sel = filePanel.getSelectedFiles();
        if (sel.size() == 1) onRequestRename(sel.get(0));
        else if (sel.size() > 1) showError("Can't rename", "Select a single item to rename.");
    }
    @Override public void onCopy() { onRequestCopy(filePanel.getSelectedFiles()); }
    @Override public void onCut() { onRequestCut(filePanel.getSelectedFiles()); }
    @Override public void onPaste() { onRequestPaste(); }

    // ---- Drag-and-drop ----------------------------------------------------

    @Override public void onRequestRefresh() { refresh(); }

    @Override public void onFilesDropped(List<File> files, File destinationFolder, boolean move) {
        if (files == null || files.isEmpty() || destinationFolder == null) return;
        StringBuilder errors = new StringBuilder();
        File lastCreated = null;
        for (File f : files) {
            if (!f.exists()) continue;
            File parent = f.getParentFile();
            if (destinationFolder.equals(parent)) continue; // already there — nothing to do

            try {
                // Honor the action the drag itself requested (the standard OS
                // modifier-key drag convention) rather than inferring move vs.
                // copy from where the file came from.
                lastCreated = move
                        ? FileOperations.moveInto(f, destinationFolder)
                        : FileOperations.copyInto(f, destinationFolder);
            } catch (IOException e) {
                errors.append(f.getName()).append(": ").append(e.getMessage()).append("\n");
            }
        }
        refresh();
        if (lastCreated != null) filePanel.selectByName(lastCreated.getName());
        if (errors.length() > 0) showError("Some items couldn't be dropped here", errors.toString());
    }

    @Override public void onToggleHidden(boolean show) {
        showHidden = show;
        refresh();
    }

    // ---- Search -----------------------------------------------------------

    @Override public void onSearch(String query) {
        if (query == null || query.isEmpty()) {
            toolbar.setSearching(false);
            refresh();
            return;
        }
        if (activeSearch != null && !activeSearch.isDone()) activeSearch.cancel(true);

        File root = currentDir;
        toolbar.setSearching(true);
        statusBar.setClipboardStatus("Searching \"" + query + "\"\u2026");
        activeSearch = new SwingWorker<>() {
            @Override protected List<FileEntry> doInBackground() {
                return FileSearchService.search(root, query, showHidden, 500, this::isCancelled);
            }
            @Override protected void done() {
                toolbar.setSearching(false);
                if (isCancelled()) return;
                try {
                    List<FileEntry> results = get();
                    filePanel.setEntries(results);
                    statusBar.setItemCount(results.size());
                    String suffix = results.size() >= 500 ? " (showing first 500)" : "";
                    statusBar.setClipboardStatus("Search results for \"" + query + "\"" + suffix);
                } catch (Exception ignored) { }
            }
        };
        activeSearch.execute();
    }

    @Override public void onCancelSearch() {
        if (activeSearch != null && !activeSearch.isDone()) {
            activeSearch.cancel(true);
        }
        toolbar.setSearching(false);
        toolbar.clearSearch();
        statusBar.setClipboardStatus("Search cancelled");
        refresh();
    }

    // ---- Selection reporting ------------------------------------------------

    @Override public void onSelectionChanged(int count, long totalBytes) {
        statusBar.setSelection(count, totalBytes);
    }

    // ---- Helpers ------------------------------------------------------------

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
