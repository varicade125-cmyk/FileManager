package filemanager.core;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * All direct interaction with the underlying operating system's filesystem
 * lives here: listing directories, creating / deleting / renaming / moving /
 * copying files and folders, computing sizes, and launching files with the
 * OS's default associated application. Every other class in the app goes
 * through this class instead of touching java.io / java.nio.file directly.
 */
public final class FileOperations {

    private FileOperations() { }

    /** Lists the immediate children of a directory as FileEntry objects. */
    public static List<FileEntry> listDirectory(File dir, boolean showHidden) throws IOException {
        List<FileEntry> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) {
            throw new IOException("Cannot read directory (permission denied or not a folder): " + dir);
        }
        for (File f : children) {
            if (!showHidden && f.isHidden()) continue;
            result.add(new FileEntry(f));
        }
        return result;
    }

    /** Creates a new, empty subdirectory. Throws if the name already exists. */
    public static File createFolder(File parent, String name) throws IOException {
        String invalidReason = FileNameValidator.validate(name);
        if (invalidReason != null) throw new IOException(invalidReason);
        File target = new File(parent, name.trim());
        if (target.exists()) throw new IOException("\"" + name + "\" already exists here.");
        if (!target.mkdir()) throw new IOException("The operating system refused to create the folder.");
        return target;
    }

    /** Creates a new, empty file. Throws if the name already exists. */
    public static File createFile(File parent, String name) throws IOException {
        String invalidReason = FileNameValidator.validate(name);
        if (invalidReason != null) throw new IOException(invalidReason);
        File target = new File(parent, name.trim());
        if (target.exists()) throw new IOException("\"" + name + "\" already exists here.");
        if (!target.createNewFile()) throw new IOException("The operating system refused to create the file.");
        return target;
    }

    /** Renames a file or folder in place. */
    public static void rename(File source, String newName) throws IOException {
        String invalidReason = FileNameValidator.validate(newName);
        if (invalidReason != null) throw new IOException(invalidReason);
        File target = new File(source.getParentFile(), newName.trim());
        if (target.exists()) {
            // On a case-insensitive filesystem (Windows, default macOS) renaming
            // "report.txt" -> "Report.txt" makes target.exists() true because the
            // target IS the source. That's a legitimate case-only rename, not a
            // name collision, so don't reject it.
            // A real case-insensitive filesystem has ONE directory entry for both
            // spellings, so the exact target name is not listed separately. If it is
            // listed, it's a distinct entry (e.g. a hard link) and must be rejected.
            String[] siblings = source.getParentFile().list();
            boolean targetListedSeparately = siblings != null
                    && java.util.Arrays.asList(siblings).contains(target.getName());
            boolean caseOnlyChange = source.getName().equalsIgnoreCase(target.getName())
                    && !source.getName().equals(target.getName())
                    && !targetListedSeparately
                    && Files.isSameFile(source.toPath(), target.toPath());
            if (!caseOnlyChange) throw new IOException("\"" + newName + "\" already exists here.");
            renameViaTempName(source, target);
            return;
        }
        Files.move(source.toPath(), target.toPath());
    }

    /** Case-only renames go through a temporary name, because moving a file onto a
     *  path that resolves to the very same file is treated as a no-op on some
     *  platforms. If the second step fails, the original name is restored. */
    private static void renameViaTempName(File source, File target) throws IOException {
        Path src = source.toPath();
        Path tmp = src.resolveSibling(source.getName() + ".rename-" + System.nanoTime() + ".tmp");
        Files.move(src, tmp);
        try {
            Files.move(tmp, target.toPath());
            if (Files.exists(tmp, LinkOption.NOFOLLOW_LINKS)) {
                // The move silently did nothing (target resolved to the same file).
                throw new IOException("The operating system didn't complete the rename.");
            }
        } catch (IOException e) {
            try { Files.move(tmp, src); } catch (IOException ignored) { }
            throw e;
        }
    }

    /** Recursively deletes a file or folder. This is permanent — it does NOT
     *  go through the OS recycle bin/trash. Use moveToTrash() for the
     *  reversible, user-facing "Delete" action, and reserve this for the
     *  explicit "Delete Permanently" (Shift+Delete) action.
     *
     *  A symbolic link is always unlinked directly rather than descended
     *  into: File.isDirectory() follows symlinks, so without this check a
     *  symlink to a folder would have the REAL folder it points to emptied
     *  out from under it instead of just having the link itself removed. */
    public static void delete(File target) throws IOException {
        if (Files.isSymbolicLink(target.toPath())) {
            Files.delete(target.toPath());
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File c : children) delete(c);
            }
        }
        Files.delete(target.toPath());
    }

    /**
     * Sends a file or folder to the operating system's recycle bin / trash
     * instead of permanently deleting it, using java.awt.Desktop's
     * MOVE_TO_TRASH action (available since Java 9). Returns false — rather
     * than throwing — if the current platform/desktop environment doesn't
     * support this, so callers can decide how to handle that (e.g. offer a
     * permanent delete instead).
     */
    public static boolean moveToTrash(File target) {
        if (!Desktop.isDesktopSupported()) return false;
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) return false;
        try {
            return desktop.moveToTrash(target);
        } catch (RuntimeException e) {
            // Some platforms throw rather than returning false on failure — treat
            // that the same as an unsuccessful move so the caller can fall back.
            return false;
        }
    }

    /** Copies a file or folder (recursively) into destinationDir. Returns the new path. */
    public static File copyInto(File source, File destinationDir) throws IOException {
        rejectIfDestinationInsideSource(source, destinationDir);
        File dest = uniqueDestination(destinationDir, source.getName());
        copyRecursive(source.toPath(), dest.toPath());
        return dest;
    }

    /** Moves (cut+paste) a file or folder into destinationDir. Returns the new path. */
    public static File moveInto(File source, File destinationDir) throws IOException {
        rejectIfDestinationInsideSource(source, destinationDir);
        File dest = uniqueDestination(destinationDir, source.getName());
        Path sourcePath = source.toPath();
        Path destPath = dest.toPath();
        try {
            Files.move(sourcePath, destPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Cross-filesystem (or otherwise non-atomic) move: fall back to copy-then-delete.
            copyRecursive(sourcePath, destPath);
            delete(source);
        }
        return dest;
    }

    /**
     * Guards against copying/moving a folder into itself or one of its own
     * subfolders (e.g. dragging "Photos" onto "Photos/Vacation", which the
     * "drop onto a folder row" feature makes easy to do by accident). Without
     * this check, copyRecursive would create the destination folder *inside*
     * the very tree it's still in the middle of walking, so its own
     * DirectoryStream would eventually see that freshly-created copy as one
     * of its children and recurse into it too — a real, always-reproducible
     * infinite-copy loop, not just a symlink-cycle edge case.
     */
    private static void rejectIfDestinationInsideSource(File source, File destinationDir) throws IOException {
        if (!source.isDirectory()) return;
        String sourcePath = source.getCanonicalPath();
        String destPath = destinationDir.getCanonicalPath();
        if (destPath.equals(sourcePath) || destPath.startsWith(sourcePath + File.separator)) {
            throw new IOException("Can't move or copy \"" + source.getName()
                    + "\" into itself or one of its own subfolders.");
        }
    }

    /** If destinationDir/name already exists, appends " (copy)", " (copy 2)", etc. */
    private static File uniqueDestination(File destinationDir, String name) {
        File candidate = new File(destinationDir, name);
        if (!candidate.exists()) return candidate;

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        boolean isDir = new File(destinationDir, name).isDirectory();
        if (!isDir && dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int counter = 2;
        File next = new File(destinationDir, base + " (copy)" + ext);
        while (next.exists()) {
            next = new File(destinationDir, base + " (copy " + counter + ")" + ext);
            counter++;
        }
        return next;
    }

    private static void copyRecursive(Path source, Path dest) throws IOException {
        // Copy a symlink as a new symlink rather than following it: dereferencing
        // it here would silently turn "copy this link" into "copy everything the
        // link points at", and would recurse forever on a symlink cycle.
        if (Files.isSymbolicLink(source)) {
            Files.createSymbolicLink(dest, Files.readSymbolicLink(source));
            return;
        }
        if (Files.isDirectory(source)) {
            Files.createDirectories(dest);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyRecursive(child, dest.resolve(child.getFileName()));
                }
            }
        } else {
            Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    /** Recursively sums the size, in bytes, of a file or folder. Symlinked
     *  folders are not descended into (their own size is negligible and
     *  counting the real target would double-count it and risks an infinite
     *  loop on a symlink cycle). */
    public static long calculateSize(File target) {
        if (Files.isSymbolicLink(target.toPath())) return 0L;
        if (target.isFile()) return target.length();
        long total = 0L;
        File[] children = target.listFiles();
        if (children == null) return 0L;
        for (File c : children) total += calculateSize(c);
        return total;
    }

    /** Counts every file+folder nested under target (not including target itself).
     *  Symlinked folders count as a single item but aren't descended into, for
     *  the same double-counting / infinite-loop reasons as calculateSize(). */
    public static int countItems(File target) {
        if (!target.isDirectory() || Files.isSymbolicLink(target.toPath())) return 0;
        File[] children = target.listFiles();
        if (children == null) return 0;
        int count = children.length;
        for (File c : children) {
            if (!Files.isSymbolicLink(c.toPath())) count += countItems(c);
        }
        return count;
    }

    /**
     * Asks the host operating system to open the file with its default
     * associated application. This is layered so a "file type Java's Desktop
     * API doesn't know" isn't a dead end: it first tries the standard
     * java.awt.Desktop API, and if that's unsupported (common on many Linux
     * setups) or fails outright, it falls back to asking the OS shell
     * directly — the same mechanism a real double-click in the native file
     * explorer would use.
     */
    public static void openWithSystem(File file) throws IOException {
        openTargetNatively(file.getAbsolutePath());
    }

    /** Opens a URL (e.g. from a .url / .desktop Internet shortcut) in the default browser. */
    public static void openUrl(String url) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(URI.create(url));
                    return;
                } catch (IOException | IllegalArgumentException ignored) {
                    // Fall through to the native shell fallback below.
                }
            }
        }
        openTargetNatively(url);
    }

    /**
     * Shared fallback path for both openWithSystem and openUrl: try
     * java.awt.Desktop first, and if it's unavailable or refuses, hand the
     * path/URL straight to the operating system's own "open" mechanism.
     */
    private static void openTargetNatively(String target) throws IOException {
        IOException lastError = null;

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                try {
                    desktop.open(new File(target));
                    return;
                } catch (IOException e) {
                    lastError = e;
                } catch (IllegalArgumentException ignored) {
                    // Not a valid local file path (e.g. it was actually a URL) — fall through.
                }
            }
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "\"\"", target);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", target);
            } else {
                pb = new ProcessBuilder("xdg-open", target);
            }
            pb.redirectErrorStream(true);
            pb.start();
        } catch (IOException e) {
            throw new IOException("No application is associated with this file type on this system.",
                    lastError != null ? lastError : e);
        }
    }

    /** Reveals/selects the file in the OS's native file browser, where supported. */
    public static void revealInSystemExplorer(File file) throws IOException {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                desktop.browseFileDirectory(file);
                return;
            }
        }
        throw new IOException("Reveal-in-explorer is not supported on this operating system.");
    }

    public static String creationTime(File file) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            return new java.text.SimpleDateFormat("MMM d, yyyy  h:mm a")
                    .format(new java.util.Date(attrs.creationTime().toMillis()));
        } catch (IOException e) {
            return "Unknown";
        }
    }

    /** Human-readable byte count, e.g. "482 KB", "3.1 GB". */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.max(1, Math.min(exp, 4)); // guard against floating-point rounding at exact powers of 1024
        String unit = "KMGT".charAt(exp - 1) + "B";
        double value = bytes / Math.pow(1024, exp);
        return String.format("%.1f %s", value, unit);
    }
}
