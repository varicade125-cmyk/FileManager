package filemanager.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Central place for the "neon-tech / HUD" color palette and shared
 * look-and-feel setup. Every UI class pulls its colors from here so the
 * whole app stays visually consistent: near-black backgrounds, a single
 * glowing cyan accent, magenta/amber/green as signal colors, and monospace
 * type throughout for a terminal / control-panel feel.
 */
public final class Theme {

    // Core palette — deep, almost-black backgrounds with a cool blue-black
    // undertone, lit by a single electric-cyan accent. Everything reads
    // like a HUD: crisp, high-contrast, a little bit of glow.
    public static final Color BG_DARKEST   = new Color(0x05, 0x07, 0x0A); // window background
    public static final Color BG_PANEL     = new Color(0x09, 0x0D, 0x12); // sidebar / toolbar
    public static final Color BG_ROW_ALT   = new Color(0x0C, 0x11, 0x17); // alternating rows
    public static final Color BG_HOVER     = new Color(0x12, 0x1B, 0x22); // hover state
    public static final Color BG_SELECTED  = new Color(0x08, 0x2A, 0x30); // selection fill (cyan-tinted)
    public static final Color BORDER       = new Color(0x16, 0x22, 0x28);

    public static final Color ACCENT       = new Color(0x00, 0xE8, 0xFF); // primary accent — electric cyan
    public static final Color ACCENT_DIM   = new Color(0x0A, 0x5C, 0x66);
    public static final Color ACCENT_2     = new Color(0xFF, 0x2E, 0xC4); // secondary accent — magenta
    public static final Color DANGER       = new Color(0xFF, 0x3B, 0x5C); // neon red
    public static final Color SUCCESS      = new Color(0x39, 0xFF, 0x9C); // neon green
    public static final Color WARNING      = new Color(0xFF, 0xC4, 0x2B); // neon amber

    public static final Color TEXT_PRIMARY   = new Color(0xDD, 0xF6, 0xFA);
    public static final Color TEXT_SECONDARY = new Color(0x5E, 0x87, 0x91);
    public static final Color TEXT_DISABLED  = new Color(0x33, 0x44, 0x4A);

    // Monospace everywhere — the whole interface reads like a terminal /
    // system console rather than a stock desktop app.
    public static final Font FONT_UI      = new Font("Consolas", Font.PLAIN, 13);
    public static final Font FONT_UI_BOLD = new Font("Consolas", Font.BOLD, 13);
    public static final Font FONT_SMALL   = new Font("Consolas", Font.PLAIN, 11);
    public static final Font FONT_MONO    = new Font("Consolas", Font.PLAIN, 12);

    private Theme() { }

    /** Applies a dark UIManager baseline so built-in dialogs (JOptionPane,
     *  file choosers, tooltips, etc.) also match the midnight theme. Call
     *  this once, before any component is created. */
    public static void apply() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }

        UIManager.put("control", BG_DARKEST);
        UIManager.put("info", BG_PANEL);
        UIManager.put("nimbusBase", BG_DARKEST);
        UIManager.put("text", TEXT_PRIMARY);

        UIManager.put("Panel.background", BG_DARKEST);
        UIManager.put("OptionPane.background", BG_PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        UIManager.put("OptionPane.buttonFont", FONT_UI);
        UIManager.put("OptionPane.messageFont", FONT_UI);

        UIManager.put("Button.background", BG_HOVER);
        UIManager.put("Button.foreground", TEXT_PRIMARY);
        UIManager.put("Button.font", FONT_UI);
        UIManager.put("Button.select", ACCENT_DIM);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));

        UIManager.put("TextField.background", BG_HOVER);
        UIManager.put("TextField.foreground", TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground", TEXT_PRIMARY);
        UIManager.put("TextField.border", new EmptyBorder(4, 8, 4, 8));
        UIManager.put("TextField.font", FONT_UI);

        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("Label.font", FONT_UI);

        UIManager.put("ToolTip.background", BG_HOVER);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));

        UIManager.put("ScrollBar.thumb", BG_HOVER);
        UIManager.put("ScrollBar.thumbDarkShadow", BG_HOVER);
        UIManager.put("ScrollBar.thumbHighlight", BG_HOVER);
        UIManager.put("ScrollBar.thumbShadow", BG_HOVER);
        UIManager.put("ScrollBar.track", BG_DARKEST);
        UIManager.put("ScrollBar.width", 12);

        // Metal (the default cross-platform L&F) doesn't reliably honor the
        // ScrollBar.* keys above for every JDK build, so scrollbars also get
        // a custom-painted UI installed directly (see styleScrollPane below).
        // These extra keys catch every other "list-like" component so
        // nothing is left with a stock light-grey background.
        UIManager.put("List.background", BG_PANEL);
        UIManager.put("List.foreground", TEXT_PRIMARY);
        UIManager.put("List.selectionBackground", BG_SELECTED);
        UIManager.put("List.selectionForeground", TEXT_PRIMARY);

        UIManager.put("ScrollPane.background", BG_DARKEST);
        UIManager.put("Viewport.background", BG_DARKEST);

        UIManager.put("Table.background", BG_DARKEST);
        UIManager.put("Table.foreground", TEXT_PRIMARY);
        UIManager.put("Table.gridColor", BG_DARKEST);
        UIManager.put("Table.selectionBackground", BG_SELECTED);
        UIManager.put("Table.selectionForeground", TEXT_PRIMARY);
        UIManager.put("TableHeader.background", BG_PANEL);
        UIManager.put("TableHeader.foreground", TEXT_SECONDARY);

        UIManager.put("CheckBox.background", BG_PANEL);
        UIManager.put("CheckBox.foreground", TEXT_PRIMARY);
        UIManager.put("CheckBox.font", FONT_UI);

        UIManager.put("MenuItem.background", BG_PANEL);
        UIManager.put("MenuItem.foreground", TEXT_PRIMARY);
        UIManager.put("MenuItem.selectionBackground", ACCENT_DIM);
        UIManager.put("MenuItem.selectionForeground", TEXT_PRIMARY);
        UIManager.put("MenuItem.font", FONT_UI);
        UIManager.put("PopupMenu.background", BG_PANEL);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("Separator.foreground", BORDER);

        UIManager.put("ProgressBar.background", BG_HOVER);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.selectionBackground", TEXT_PRIMARY);
        UIManager.put("ProgressBar.selectionForeground", BG_DARKEST);
    }

    /** Flat button with a thin low-glow border, used across toolbars and dialogs. */
    public static JButton flatButton(String text) {
        JButton b = new JButton(text.toUpperCase());
        b.setFocusPainted(false);
        b.setBackground(BG_HOVER);
        b.setForeground(TEXT_PRIMARY);
        b.setFont(FONT_UI);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(6, 12, 6, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(e -> {
            if (b.getModel().isRollover()) {
                b.setBackground(ACCENT_DIM);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ACCENT),
                        new EmptyBorder(6, 12, 6, 12)));
            } else {
                b.setBackground(BG_HOVER);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(6, 12, 6, 12)));
            }
        });
        return b;
    }

    /** Small icon-style toolbar button (arrows, refresh, etc.). */
    public static JButton toolButton(String glyph, String tooltip) {
        JButton b = new JButton(glyph);
        b.setFocusPainted(false);
        b.setBackground(BG_HOVER);
        b.setForeground(ACCENT);
        b.setToolTipText(tooltip);
        b.setFont(FONT_UI_BOLD);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(6, 10, 6, 10)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(e -> {
            if (b.getModel().isRollover()) {
                b.setBackground(ACCENT_DIM);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ACCENT),
                        new EmptyBorder(6, 10, 6, 10)));
            } else {
                b.setBackground(BG_HOVER);
                b.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(6, 10, 6, 10)));
            }
        });
        return b;
    }

    /** A primary (accent-colored) button, used for confirm actions. Reads as
     *  a lit-up cyan "power" button against the dark chrome around it. */
    public static JButton accentButton(String text) {
        JButton b = new JButton(text.toUpperCase());
        b.setFocusPainted(false);
        b.setForeground(BG_DARKEST);
        b.setFont(FONT_UI_BOLD);
        b.setBackground(ACCENT);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT),
                new EmptyBorder(6, 12, 6, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addChangeListener(e -> {
            if (b.getModel().isRollover()) {
                b.setBackground(TEXT_PRIMARY);
            } else {
                b.setBackground(ACCENT);
            }
        });
        return b;
    }

    /**
     * Fully themes a JScrollPane: transparent border, dark viewport, and a
     * custom-painted scrollbar (thin, no arrow buttons, rounded accent
     * thumb). Called instead of touching JScrollPane/JScrollBar directly so
     * every scrollable panel in the app looks identical.
     */
    public static void styleScrollPane(JScrollPane pane, Color background) {
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(true);
        pane.setBackground(background);
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(background);
        styleScrollBar(pane.getVerticalScrollBar());
        styleScrollBar(pane.getHorizontalScrollBar());
    }

    private static void styleScrollBar(JScrollBar bar) {
        if (bar == null) return;
        bar.setPreferredSize(new Dimension(10, 10));
        bar.setUnitIncrement(18);
        bar.setOpaque(true);
        bar.setBackground(BG_DARKEST);
        bar.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = ACCENT_DIM;
                this.trackColor = BG_DARKEST;
            }

            @Override protected JButton createDecreaseButton(int orientation) { return zeroSizeButton(); }
            @Override protected JButton createIncreaseButton(int orientation) { return zeroSizeButton(); }

            private JButton zeroSizeButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }

            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                g.setColor(BG_DARKEST);
                g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
            }

            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !c.isEnabled()) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor);
                int pad = 2;
                g2.fillRoundRect(thumbBounds.x + pad, thumbBounds.y + pad,
                        thumbBounds.width - pad * 2, thumbBounds.height - pad * 2, 6, 6);
                g2.dispose();
            }
        });
    }
}
