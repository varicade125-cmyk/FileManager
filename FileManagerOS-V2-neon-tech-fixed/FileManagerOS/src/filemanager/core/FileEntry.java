package filemanager.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Lightweight wrapper around java.io.File that pre-computes the values the
 * table needs to display (size, type label, last-modified) so the UI layer
 * never has to touch the filesystem directly.
 */
public class FileEntry {

    private final File file;
    private final boolean directory;
    private final long size;
    private final long lastModified;

    public FileEntry(File file) {
        this.file = file;
        this.directory = file.isDirectory();
        long sz = 0L;
        long mod = 0L;
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            mod = attrs.lastModifiedTime().toMillis();
            sz = directory ? 0L : attrs.size();
        } catch (IOException e) {
            mod = file.lastModified();
            sz = directory ? 0L : file.length();
        }
        this.size = sz;
        this.lastModified = mod;
    }

    public File getFile() { return file; }
    public String getName() { return file.getName(); }
    public boolean isDirectory() { return directory; }
    public boolean isHidden() { return file.isHidden(); }
    public long getSizeBytes() { return size; }
    public long getLastModifiedMillis() { return lastModified; }

    public String getExtension() {
        if (directory) return "";
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase();
    }

    public String getTypeLabel() {
        if (isSymlink()) return directory ? "Shortcut (Folder)" : "Shortcut";
        if (isWindowsShortcut() || isDesktopEntry()) return "Shortcut";
        if (isInternetShortcut()) return "Internet Shortcut";
        if (directory) return "Folder";
        String ext = getExtension();
        if (ext.isEmpty()) return "File";
        return ext.toUpperCase() + " File";
    }

    public boolean isSymlink() { return ShortcutResolver.isSymlink(file); }
    public boolean isWindowsShortcut() { return ShortcutResolver.isWindowsShortcut(file); }
    public boolean isDesktopEntry() { return ShortcutResolver.isDesktopEntry(file); }
    public boolean isInternetShortcut() { return ShortcutResolver.isInternetShortcut(file); }
    public boolean isLinkLike() { return ShortcutResolver.isLinkLike(file); }

    public String getFormattedSize() {
        if (directory) return "";
        return FileOperations.formatSize(size);
    }

    public String getFormattedDate() {
        if (lastModified <= 0) return "";
        return new SimpleDateFormat("MMM d, yyyy  h:mm a").format(new Date(lastModified));
    }
}
