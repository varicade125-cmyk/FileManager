package filemanager.ui;

import filemanager.core.FileOperations;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Thin footer bar reporting item counts, current selection, and clipboard state. */
public class StatusBar extends JPanel {

    private final JLabel countLabel = new JLabel();
    private final JLabel selectionLabel = new JLabel();
    private final JLabel clipboardLabel = new JLabel();

    public StatusBar() {
        setLayout(new BorderLayout());
        setBackground(Theme.BG_PANEL);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(6, 14, 6, 14)));

        style(countLabel);
        style(selectionLabel);
        style(clipboardLabel);
        clipboardLabel.setForeground(Theme.ACCENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        left.setOpaque(false);
        left.add(countLabel);
        left.add(selectionLabel);

        add(left, BorderLayout.WEST);
        add(clipboardLabel, BorderLayout.EAST);

        setItemCount(0);
        setSelection(0, 0);
        setClipboardStatus(null);
    }

    private void style(JLabel label) {
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setFont(Theme.FONT_SMALL);
    }

    public void setItemCount(int count) {
        countLabel.setText(count + (count == 1 ? " item" : " items"));
    }

    public void setSelection(int count, long totalBytes) {
        if (count == 0) {
            selectionLabel.setText("");
        } else {
            selectionLabel.setText(count + " selected  (" + FileOperations.formatSize(totalBytes) + ")");
        }
    }

    /** Pass null to clear, or a description like "3 items cut" / "2 items copied". */
    public void setClipboardStatus(String text) {
        clipboardLabel.setText(text == null ? "" : text);
    }
}
