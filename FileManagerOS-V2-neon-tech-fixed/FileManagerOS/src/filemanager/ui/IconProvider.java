package filemanager.ui;

import filemanager.core.FileEntry;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Draws small, flat, vector-style icons directly with Graphics2D so the app
 * needs no external image files and every icon matches the midnight theme
 * exactly. Color is chosen from the file extension.
 */
public final class IconProvider {

    private static final Set<String> IMAGE_EXT = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico"));
    private static final Set<String> CODE_EXT = new HashSet<>(Arrays.asList(
            "java", "py", "js", "ts", "c", "cpp", "h", "cs", "html", "css", "json", "xml", "sh"));
    private static final Set<String> DOC_EXT = new HashSet<>(Arrays.asList(
            "txt", "md", "doc", "docx", "pdf", "rtf"));
    private static final Set<String> ARCHIVE_EXT = new HashSet<>(Arrays.asList(
            "zip", "rar", "7z", "tar", "gz"));
    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            "mp3", "wav", "flac", "ogg", "m4a"));
    private static final Set<String> VIDEO_EXT = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "avi", "mov", "webm"));

    private IconProvider() { }

    public static Icon iconFor(FileEntry entry) {
        int size = 18;
        Icon base;
        if (entry.isDirectory()) {
            base = folderIcon(size, Theme.ACCENT);
        } else {
            Color c = colorForExtension(entry.getExtension());
            base = fileIcon(size, c);
        }
        return entry.isLinkLike() ? withShortcutBadge(base) : base;
    }

    /** Overlays a small curved-arrow badge (like a Windows/Explorer shortcut arrow) on a base icon. */
    private static Icon withShortcutBadge(Icon base) {
        return new Icon() {
            public int getIconWidth() { return base.getIconWidth(); }
            public int getIconHeight() { return base.getIconHeight(); }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                base.paintIcon(c, g, x, y);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int bx = x;
                int by = y + getIconHeight() - 9;
                g2.setColor(Theme.BG_DARKEST);
                g2.fillOval(bx, by, 10, 10);
                g2.setColor(Theme.TEXT_PRIMARY);
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawLine(bx + 3, by + 7, bx + 7, by + 3);
                g2.drawLine(bx + 4, by + 3, bx + 7, by + 3);
                g2.drawLine(bx + 7, by + 3, bx + 7, by + 6);
                g2.dispose();
            }
        };
    }

    private static Color colorForExtension(String ext) {
        if (IMAGE_EXT.contains(ext)) return new Color(0xFF, 0xA6, 0x3D);   // neon orange
        if (CODE_EXT.contains(ext)) return Theme.SUCCESS;                  // neon green
        if (DOC_EXT.contains(ext)) return Theme.ACCENT;                    // electric cyan
        if (ARCHIVE_EXT.contains(ext)) return Theme.WARNING;               // neon amber
        if (AUDIO_EXT.contains(ext)) return Theme.ACCENT_2;                // magenta
        if (VIDEO_EXT.contains(ext)) return Theme.DANGER;                  // neon red
        return Theme.TEXT_SECONDARY;
    }

    public static Icon folderIcon(int size, Color color) {
        return new Icon() {
            public int getIconWidth() { return size + 4; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                int w = size + 2, h = (int) (size * 0.75);
                int top = y + size - h;
                // Tab
                g2.fillRoundRect(x, top - 3, (int) (w * 0.45), 5, 3, 3);
                // Body
                g2.fillRoundRect(x, top, w, h, 4, 4);
                g2.dispose();
            }
        };
    }

    public static Icon fileIcon(int size, Color color) {
        return new Icon() {
            public int getIconWidth() { return size - 2; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = size - 4, h = size;
                int fold = 5;
                Polygon body = new Polygon();
                body.addPoint(x, y);
                body.addPoint(x + w - fold, y);
                body.addPoint(x + w, y + fold);
                body.addPoint(x + w, y + h);
                body.addPoint(x, y + h);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
                g2.fillPolygon(body);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawPolygon(body);
                g2.drawLine(x + w - fold, y, x + w - fold, y + fold);
                g2.drawLine(x + w - fold, y + fold, x + w, y + fold);
                // little text lines
                g2.setStroke(new BasicStroke(1f));
                for (int i = 0; i < 3; i++) {
                    int ly = y + h - 5 - (i * 4);
                    if (ly > y + fold + 2) g2.drawLine(x + 3, ly, x + w - 4, ly);
                }
                g2.dispose();
            }
        };
    }

    public static Icon glyph(String text, Color color, int size) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setFont(Theme.FONT_UI_BOLD.deriveFont((float) size - 2));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(text)) / 2;
                int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(text, tx, ty);
                g2.dispose();
            }
        };
    }

    /** A simple flat magnifying-glass icon, used next to the search box. */
    public static Icon magnifyingGlassIcon(int size, Color color) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int circleD = (int) (size * 0.62);
                int cx = x + 1;
                int cy = y + 1;
                g2.drawOval(cx, cy, circleD, circleD);
                int handleStart = (int) (circleD * 0.82);
                g2.drawLine(cx + handleStart, cy + handleStart, x + size - 1, y + size - 1);
                g2.dispose();
            }
        };
    }
}
