package filemanager.ui;

import javax.swing.*;
import java.awt.*;

/**
 * A plain JTextField that additionally paints light grey "hint" text
 * (e.g. "Search this folder...") whenever it's empty, disappearing as soon
 * as the person starts typing. Swing has no built-in placeholder support,
 * so this draws it manually in paintComponent.
 */
public class PlaceholderTextField extends JTextField {

    private String placeholder;

    public PlaceholderTextField(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setPlaceholder(String text) {
        this.placeholder = text;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (placeholder == null || placeholder.isEmpty()) return;
        if (getText() != null && !getText().isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.TEXT_SECONDARY);
        g2.setFont(getFont());
        Insets insets = getInsets();
        FontMetrics fm = g2.getFontMetrics();
        int x = insets.left;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(placeholder, x, y);
        g2.dispose();
    }
}
