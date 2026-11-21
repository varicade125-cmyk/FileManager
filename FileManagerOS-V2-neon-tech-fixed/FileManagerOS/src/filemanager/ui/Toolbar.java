package filemanager.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.io.File;

/**
 * Top bar: navigation buttons, an editable address/path field, quick-action
 * buttons (new folder/file, delete, rename), and a live search box.
 */
public class Toolbar extends JPanel {

    public interface Listener {
        void onBack();
        void onForward();
        void onUp();
        void onRefresh();
        void onNavigateToPath(String path);
        void onNewFolder();
        void onNewFile();
        void onDelete(boolean permanent);
        void onRename();
        void onCopy();
        void onCut();
        void onPaste();
        void onSearch(String query);
        void onCancelSearch();
        void onToggleHidden(boolean show);
        void onUndo();
    }

    private final JTextField pathField;
    private final JTextField searchField;
    private final JButton backBtn, forwardBtn, upBtn;
    private final JCheckBox hiddenToggle;
    private final JPanel searchStatusPanel;
    private final CardLayout searchStatusLayout = new CardLayout();
    private final JProgressBar searchProgress;

    public Toolbar(Listener listener) {
        setLayout(new BorderLayout(8, 8));
        setBackground(Theme.BG_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.ACCENT_DIM),
                new EmptyBorder(10, 12, 10, 12)));

        // --- navigation row ---
        JPanel navRow = new JPanel(new BorderLayout(8, 0));
        navRow.setOpaque(false);

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navButtons.setOpaque(false);
        backBtn = Theme.toolButton("\u2190", "Back");
        forwardBtn = Theme.toolButton("\u2192", "Forward");
        upBtn = Theme.toolButton("\u2191", "Up one level");
        JButton refreshBtn = Theme.toolButton("\u27F3", "Refresh");
        backBtn.addActionListener(e -> listener.onBack());
        forwardBtn.addActionListener(e -> listener.onForward());
        upBtn.addActionListener(e -> listener.onUp());
        refreshBtn.addActionListener(e -> listener.onRefresh());
        navButtons.add(backBtn);
        navButtons.add(forwardBtn);
        navButtons.add(upBtn);
        navButtons.add(refreshBtn);

        pathField = new JTextField();
        pathField.setBackground(Theme.BG_DARKEST);
        pathField.setForeground(Theme.TEXT_PRIMARY);
        pathField.setCaretColor(Theme.TEXT_PRIMARY);
        pathField.setFont(Theme.FONT_MONO);
        pathField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                new EmptyBorder(6, 10, 6, 10)));
        pathField.addActionListener(e -> listener.onNavigateToPath(pathField.getText().trim()));

        searchField = new PlaceholderTextField("Search this folder...");
        searchField.setBackground(Theme.BG_DARKEST);
        searchField.setForeground(Theme.TEXT_PRIMARY);
        searchField.setCaretColor(Theme.TEXT_PRIMARY);
        searchField.setFont(Theme.FONT_UI);
        searchField.setOpaque(false);
        searchField.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 8));
        searchField.addActionListener(e -> listener.onSearch(searchField.getText().trim()));

        JLabel searchIcon = new JLabel(IconProvider.magnifyingGlassIcon(13, Theme.TEXT_SECONDARY));
        searchIcon.setBorder(new EmptyBorder(0, 10, 0, 4));

        JPanel searchBox = new JPanel(new BorderLayout());
        searchBox.setBackground(Theme.BG_DARKEST);
        searchBox.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        searchBox.add(searchIcon, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);
        searchBox.setPreferredSize(new Dimension(220, 32));

        // Shown only while a search is running: a thin indeterminate progress
        // bar (we don't know the total file count up front, so a determinate
        // percentage isn't meaningful) plus a button to stop it early. Built
        // with CardLayout, swapping between an empty card and this one, so
        // the toolbar doesn't visibly resize/jump when a search starts.
        searchProgress = new JProgressBar();
        searchProgress.setIndeterminate(true);
        searchProgress.setPreferredSize(new Dimension(70, 8));
        searchProgress.setBorderPainted(false);
        searchProgress.setBackground(Theme.BG_HOVER);
        searchProgress.setForeground(Theme.ACCENT);

        JButton cancelSearchBtn = Theme.toolButton("\u2715", "Cancel search");
        cancelSearchBtn.setForeground(Theme.DANGER);
        cancelSearchBtn.setFont(Theme.FONT_SMALL);
        cancelSearchBtn.setBorder(new EmptyBorder(4, 8, 4, 8));
        cancelSearchBtn.addActionListener(e -> listener.onCancelSearch());

        JPanel searchingCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchingCard.setOpaque(false);
        searchingCard.add(searchProgress);
        searchingCard.add(cancelSearchBtn);

        JPanel idleCard = new JPanel();
        idleCard.setOpaque(false);

        searchStatusPanel = new JPanel(searchStatusLayout);
        searchStatusPanel.setOpaque(false);
        searchStatusPanel.setPreferredSize(new Dimension(100, 32));
        searchStatusPanel.add(idleCard, "idle");
        searchStatusPanel.add(searchingCard, "searching");
        searchStatusLayout.show(searchStatusPanel, "idle");

        JPanel searchArea = new JPanel(new BorderLayout(6, 0));
        searchArea.setOpaque(false);
        searchArea.add(searchBox, BorderLayout.CENTER);
        searchArea.add(searchStatusPanel, BorderLayout.EAST);

        JPanel centerFields = new JPanel(new BorderLayout(8, 0));
        centerFields.setOpaque(false);
        centerFields.add(pathField, BorderLayout.CENTER);
        centerFields.add(searchArea, BorderLayout.EAST);

        navRow.add(navButtons, BorderLayout.WEST);
        navRow.add(centerFields, BorderLayout.CENTER);

        // --- action row ---
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionRow.setOpaque(false);
        JButton newFolderBtn = Theme.flatButton("New Folder");
        JButton newFileBtn = Theme.flatButton("New File");
        JButton renameBtn = Theme.flatButton("Rename");
        JButton copyBtn = Theme.flatButton("Copy");
        JButton cutBtn = Theme.flatButton("Cut");
        JButton pasteBtn = Theme.flatButton("Paste");
        JButton deleteBtn = Theme.flatButton("Delete");
        deleteBtn.setForeground(Theme.DANGER);
        deleteBtn.setToolTipText("Delete (moves to recycle bin) — hold Shift to delete permanently");
        JButton undoBtn = Theme.toolButton("\u21B6", "Undo last delete (Ctrl+Z) — restores from the recycle bin");

        newFolderBtn.addActionListener(e -> listener.onNewFolder());
        newFileBtn.addActionListener(e -> listener.onNewFile());
        renameBtn.addActionListener(e -> listener.onRename());
        copyBtn.addActionListener(e -> listener.onCopy());
        cutBtn.addActionListener(e -> listener.onCut());
        pasteBtn.addActionListener(e -> listener.onPaste());
        deleteBtn.addActionListener(e -> {
            boolean shiftHeld = (e.getModifiers() & InputEvent.SHIFT_MASK) != 0;
            listener.onDelete(shiftHeld);
        });
        undoBtn.addActionListener(e -> listener.onUndo());

        hiddenToggle = new JCheckBox("Show hidden");
        hiddenToggle.setOpaque(false);
        hiddenToggle.setForeground(Theme.TEXT_SECONDARY);
        hiddenToggle.setFont(Theme.FONT_SMALL);
        hiddenToggle.addActionListener(e -> listener.onToggleHidden(hiddenToggle.isSelected()));

        actionRow.add(newFolderBtn);
        actionRow.add(newFileBtn);
        actionRow.add(renameBtn);
        actionRow.add(copyBtn);
        actionRow.add(cutBtn);
        actionRow.add(pasteBtn);
        actionRow.add(deleteBtn);
        actionRow.add(undoBtn);
        actionRow.add(Box.createHorizontalStrut(16));
        actionRow.add(hiddenToggle);

        add(navRow, BorderLayout.NORTH);
        add(actionRow, BorderLayout.SOUTH);
    }

    public void setPath(File dir) {
        pathField.setText(dir.getAbsolutePath());
    }

    public void setNavEnabled(boolean back, boolean forward, boolean up) {
        backBtn.setEnabled(back);
        forwardBtn.setEnabled(forward);
        upBtn.setEnabled(up);
    }

    public void clearSearch() {
        searchField.setText("");
    }

    public void focusSearch() {
        searchField.requestFocusInWindow();
    }

    /** Shows/hides the search progress indicator and cancel button without
     *  the toolbar visibly resizing (see the CardLayout setup above). */
    public void setSearching(boolean searching) {
        searchStatusLayout.show(searchStatusPanel, searching ? "searching" : "idle");
    }
}
