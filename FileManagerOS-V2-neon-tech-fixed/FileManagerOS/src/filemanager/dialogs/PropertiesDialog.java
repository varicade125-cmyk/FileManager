package filemanager.dialogs;

import filemanager.core.FileOperations;
import filemanager.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Modal "Properties" panel for one or more selected files/folders. Size for
 * folders is computed recursively on a background thread (via SwingWorker)
 * so the UI never freezes while walking large directory trees.
 */
public final class PropertiesDialog {

    private PropertiesDialog() { }

    public static void show(Component parent, List<File> files) {
        if (files.isEmpty()) return;

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent),
                "Properties", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.getContentPane().setBackground(Theme.BG_PANEL);
        dialog.setLayout(new BorderLayout());
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.BG_PANEL);
        content.setBorder(new EmptyBorder(20, 22, 16, 22));

        boolean single = files.size() == 1;
        File first = files.get(0);

        JLabel title = new JLabel(single ? first.getName() : files.size() + " items selected");
        title.setFont(Theme.FONT_UI_BOLD.deriveFont(16f));
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(14));

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 0, 4, 16);

        int row = 0;
        row = addRow(grid, gc, row, "Location", single ? first.getParent() : files.size() + " files");
        if (single) {
            row = addRow(grid, gc, row, "Type", first.isDirectory() ? "Folder" : typeLabel(first));
            row = addRow(grid, gc, row, "Created", FileOperations.creationTime(first));
            row = addRow(grid, gc, row, "Modified",
                    new java.text.SimpleDateFormat("MMM d, yyyy  h:mm a").format(new java.util.Date(first.lastModified())));
        }

        JLabel sizeLabel = new JLabel("Calculating\u2026");
        style(sizeLabel);
        gc.gridx = 1; gc.gridy = row;
        JLabel sizeCaption = captionLabel("Size");
        gc.gridx = 0;
        grid.add(sizeCaption, gc);
        gc.gridx = 1;
        grid.add(sizeLabel, gc);
        row++;

        content.add(grid);
        content.add(Box.createVerticalStrut(10));

        JCheckBox readOnlyBox = null;
        if (single) {
            readOnlyBox = new JCheckBox("Read-only");
            readOnlyBox.setOpaque(false);
            readOnlyBox.setForeground(Theme.TEXT_PRIMARY);
            readOnlyBox.setFont(Theme.FONT_UI);
            readOnlyBox.setSelected(!first.canWrite());
            readOnlyBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(readOnlyBox);
            content.add(Box.createVerticalStrut(10));
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton close = Theme.accentButton("Close");
        JCheckBox finalReadOnlyBox = readOnlyBox;
        File finalFirst = first;
        close.addActionListener(e -> {
            if (finalReadOnlyBox != null) {
                finalFirst.setWritable(!finalReadOnlyBox.isSelected());
            }
            dialog.dispose();
        });
        buttons.add(close);
        content.add(buttons);

        dialog.add(content, BorderLayout.CENTER);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(360, dialog.getHeight()));
        dialog.setLocationRelativeTo(parent);

        // Compute size (and, for folders, item count) off the EDT.
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override protected String doInBackground() {
                long total = 0L;
                int itemCount = 0;
                for (File f : files) {
                    total += FileOperations.calculateSize(f);
                    if (f.isDirectory()) itemCount += FileOperations.countItems(f);
                }
                String sizeText = FileOperations.formatSize(total);
                if (itemCount > 0) sizeText += "   (" + itemCount + " items)";
                return sizeText;
            }
            @Override protected void done() {
                try {
                    sizeLabel.setText(get());
                } catch (Exception ex) {
                    sizeLabel.setText("Unknown");
                }
            }
        };
        worker.execute();

        dialog.setVisible(true);
    }

    private static String typeLabel(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) return "File";
        return name.substring(dot + 1).toUpperCase() + " File";
    }

    private static int addRow(JPanel grid, GridBagConstraints gc, int row, String caption, String value) {
        gc.gridx = 0; gc.gridy = row;
        grid.add(captionLabel(caption), gc);
        gc.gridx = 1;
        JLabel valueLabel = new JLabel(value == null ? "" : value);
        style(valueLabel);
        grid.add(valueLabel, gc);
        return row + 1;
    }

    private static JLabel captionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT_SECONDARY);
        l.setFont(Theme.FONT_SMALL);
        return l;
    }

    private static void style(JLabel l) {
        l.setForeground(Theme.TEXT_PRIMARY);
        l.setFont(Theme.FONT_UI);
    }
}
