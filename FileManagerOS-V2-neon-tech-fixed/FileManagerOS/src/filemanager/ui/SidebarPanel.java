package filemanager.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Left-hand navigation rail: common folders (Home, Desktop, Documents,
 * Downloads, Pictures) plus every filesystem root the OS reports
 * (C:\, D:\ on Windows; / on Linux/macOS; separate volumes if mounted).
 */
public class SidebarPanel extends JPanel {

    public interface Listener {
        void onLocationSelected(File location);
    }

    public static class Entry {
        final String label;
        final File location;
        final boolean separator;
        Entry(String label, File location) { this(label, location, false); }
        Entry(String label, File location, boolean separator) {
            this.label = label;
            this.location = location;
            this.separator = separator;
        }
    }

    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private final JList<Entry> list;
    private boolean suppressSelectionEvents = false;

    public SidebarPanel(Listener listener) {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.ACCENT_DIM),
                new EmptyBorder(14, 10, 14, 10)));
        setPreferredSize(new Dimension(190, 0));

        JLabel header = new JLabel("QUICK ACCESS");
        header.setForeground(Theme.TEXT_SECONDARY);
        header.setFont(Theme.FONT_SMALL);
        header.setBorder(new EmptyBorder(0, 8, 8, 0));
        add(header, BorderLayout.NORTH);

        buildEntries();

        list = new JList<>(listModel);
        list.setBackground(Theme.BG_PANEL);
        list.setSelectionBackground(Theme.BG_SELECTED);
        list.setSelectionForeground(Theme.TEXT_PRIMARY);
        list.setFont(Theme.FONT_UI);
        list.setFixedCellHeight(32);
        list.setCellRenderer(new EntryRenderer());
        list.setBorder(BorderFactory.createEmptyBorder());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelectionEvents) {
                Entry entry = list.getSelectedValue();
                if (entry != null && !entry.separator && entry.location.isDirectory()) {
                    listener.onLocationSelected(entry.location);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        Theme.styleScrollPane(scroll, Theme.BG_PANEL);
        add(scroll, BorderLayout.CENTER);
    }

    private void buildEntries() {
        String home = System.getProperty("user.home");
        List<Entry> quick = new ArrayList<>();
        quick.add(new Entry("\uD83C\uDFE0  Home", new File(home)));
        addIfExists(quick, "\uD83D\uDDA5  Desktop", new File(home, "Desktop"));
        addIfExists(quick, "\uD83D\uDCC4  Documents", new File(home, "Documents"));
        addIfExists(quick, "\u2B07  Downloads", new File(home, "Downloads"));
        addIfExists(quick, "\uD83D\uDDBC  Pictures", new File(home, "Pictures"));
        for (Entry e : quick) listModel.addElement(e);

        listModel.addElement(new Entry("\u2014\u2014 DRIVES \u2014\u2014", new File(home), true));
        for (File root : File.listRoots()) {
            String label = root.getAbsolutePath();
            listModel.addElement(new Entry("\uD83D\uDDB4  " + label, root));
        }
    }

    private void addIfExists(List<Entry> list, String label, File dir) {
        if (dir.isDirectory()) list.add(new Entry(label, dir));
    }

    public void setSelectedIfMatches(File dir) {
        suppressSelectionEvents = true;
        try {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).location.equals(dir)) {
                    list.setSelectedIndex(i);
                    return;
                }
            }
            list.clearSelection();
        } finally {
            suppressSelectionEvents = false;
        }
    }

    private class EntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                        boolean isSelected, boolean hasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, isSelected, hasFocus);
            Entry entry = (Entry) value;
            label.setText(entry.label);
            label.setBorder(new EmptyBorder(0, 10, 0, 0));
            boolean isSeparator = entry.separator;
            if (isSeparator) {
                label.setForeground(Theme.TEXT_DISABLED);
                label.setFont(Theme.FONT_SMALL);
                label.setEnabled(false);
            } else {
                label.setForeground(Theme.TEXT_PRIMARY);
                label.setFont(Theme.FONT_UI);
            }
            label.setOpaque(true);
            label.setBackground(isSelected && !isSeparator ? Theme.BG_SELECTED : Theme.BG_PANEL);
            if (isSelected && !isSeparator) {
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.ACCENT),
                        new EmptyBorder(0, 7, 0, 0)));
            }
            return label;
        }
    }
}
