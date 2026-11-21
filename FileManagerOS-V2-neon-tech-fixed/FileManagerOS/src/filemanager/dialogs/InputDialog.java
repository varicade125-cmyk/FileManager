package filemanager.dialogs;

import filemanager.core.FileNameValidator;
import filemanager.ui.Theme;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/** Reusable dark, minimalist modal prompt used for rename / new folder / new file. */
public final class InputDialog {

    private InputDialog() { }

    /** Shows the prompt and returns the entered text, or null if the user canceled. */
    public static String show(Component parent, String title, String promptText, String initialValue) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.getContentPane().setBackground(Theme.BG_PANEL);
        dialog.setLayout(new BorderLayout());
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.BG_PANEL);
        content.setBorder(new EmptyBorder(20, 20, 16, 20));

        JLabel label = new JLabel(promptText);
        label.setForeground(Theme.TEXT_SECONDARY);
        label.setFont(Theme.FONT_UI);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField field = new JTextField(initialValue == null ? "" : initialValue);
        field.setBackground(Theme.BG_DARKEST);
        field.setForeground(Theme.TEXT_PRIMARY);
        field.setCaretColor(Theme.TEXT_PRIMARY);
        field.setFont(Theme.FONT_UI);
        Border normalBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT_DIM),
                new EmptyBorder(8, 10, 8, 10));
        Border errorBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.DANGER),
                new EmptyBorder(8, 10, 8, 10));
        field.setBorder(normalBorder);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(360, 36));
        field.setPreferredSize(new Dimension(320, 36));

        JLabel errorLabel = new JLabel(" ");
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setFont(Theme.FONT_SMALL);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(label);
        content.add(Box.createVerticalStrut(8));
        content.add(field);
        content.add(Box.createVerticalStrut(4));
        content.add(errorLabel);
        content.add(Box.createVerticalStrut(14));

        final String[] result = { null };

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = Theme.flatButton("Cancel");
        JButton ok = Theme.accentButton("OK");
        cancel.addActionListener(e -> dialog.dispose());

        Runnable attemptAccept = () -> {
            String text = field.getText().trim();
            String invalidReason = FileNameValidator.validate(text);
            if (invalidReason != null) {
                errorLabel.setText(invalidReason);
                field.setBorder(errorBorder);
                return;
            }
            result[0] = text;
            dialog.dispose();
        };
        ok.addActionListener(e -> attemptAccept.run());

        // Clear the error state as soon as the person starts fixing the name,
        // rather than leaving a stale red border up after they've corrected it.
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { clearError(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { clearError(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { clearError(); }
            private void clearError() {
                errorLabel.setText(" ");
                field.setBorder(normalBorder);
            }
        });

        field.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) attemptAccept.run();
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) dialog.dispose();
            }
        });
        buttons.add(cancel);
        buttons.add(ok);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(buttons);

        dialog.add(content, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        // Pre-select the base name (without extension) for convenient renaming.
        if (initialValue != null && initialValue.contains(".")) {
            int dot = initialValue.lastIndexOf('.');
            field.select(0, dot);
        } else {
            field.selectAll();
        }
        field.requestFocusInWindow();

        dialog.setVisible(true);
        return result[0];
    }
}

