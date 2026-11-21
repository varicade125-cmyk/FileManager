package filemanager.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the various kinds of "shortcut" a file manager needs to follow
 * so double-clicking one does the right thing instead of just handing an
 * opaque file to the OS and hoping:
 *
 *  - Windows ".lnk" shortcuts (binary Shell Link format) — may point to a
 *    folder, a file, or an application.
 *  - Windows ".url" Internet Shortcuts (plain INI-style text) — point to a
 *    web address.
 *  - Linux/freedesktop ".desktop" entries — may be a "Link" (points to a
 *    file/folder/URL) or an "Application" launcher (points to a command).
 *  - Symbolic links — java.io.File already follows these transparently for
 *    isFile()/isDirectory(), so no special resolution is needed there; this
 *    class only exposes isSymlink() so the UI can label/badge them.
 *
 * The .lnk parser is a lightweight, best-effort heuristic, not a full
 * MS-SHLLINK implementation: it scans the raw bytes for an embedded
 * absolute path and only trusts a match that actually exists on disk. If a
 * shortcut can't be confidently resolved, callers should fall back to
 * handing the original file to the operating system, which knows how to
 * follow its own shortcuts natively.
 */
public final class ShortcutResolver {

    private ShortcutResolver() { }

    public static boolean isWindowsShortcut(File f) { return hasExtension(f, "lnk"); }
    public static boolean isInternetShortcut(File f) { return hasExtension(f, "url"); }
    public static boolean isDesktopEntry(File f) { return hasExtension(f, "desktop"); }
    public static boolean isSymlink(File f) { return Files.isSymbolicLink(f.toPath()); }

    /** True for anything this class knows how to treat as a link/shortcut. */
    public static boolean isLinkLike(File f) {
        return isWindowsShortcut(f) || isInternetShortcut(f) || isDesktopEntry(f) || isSymlink(f);
    }

    private static boolean hasExtension(File f, String ext) {
        return f.getName().toLowerCase().endsWith("." + ext);
    }

    /**
     * Resolves a shortcut to the local file or folder it points to.
     * Returns null if this isn't a type this class understands, or if the
     * target couldn't be confidently determined (caller should then open
     * the original file and let the OS interpret it).
     */
    public static File resolveFileTarget(File file) {
        try {
            if (isWindowsShortcut(file)) return resolveWindowsShortcut(file);
            if (isDesktopEntry(file)) return resolveDesktopEntryFile(file);
        } catch (IOException ignored) {
            // Fall through — caller will let the OS handle the raw file instead.
        }
        return null;
    }

    /** Resolves a web-link shortcut (.url, or a .desktop Type=Link) to its URL. Null if none. */
    public static String resolveUrlTarget(File file) {
        try {
            if (isInternetShortcut(file)) return readIniValue(file, "URL");
            if (isDesktopEntry(file)) return readIniValue(file, "URL");
        } catch (IOException ignored) { }
        return null;
    }

    // ---- Windows .lnk: best-effort binary scan -----------------------------

    // Matches an absolute Windows path (drive letter, colon, backslash, then
    // any run of non-control characters) embedded anywhere in the raw bytes.
    private static final Pattern WINDOWS_PATH = Pattern.compile("[A-Za-z]:\\\\[^\\x00-\\x1F]*");

    private static File resolveWindowsShortcut(File lnk) throws IOException {
        byte[] data = Files.readAllBytes(lnk.toPath());
        if (data.length < 20) return null;

        List<String> candidates = new ArrayList<>();
        collectPathMatches(new String(data, StandardCharsets.ISO_8859_1), candidates);
        collectPathMatches(new String(data, StandardCharsets.UTF_16LE), candidates);
        // Longer matches are more likely to be the real, complete path rather
        // than a truncated fragment, so try those first.
        candidates.sort((a, b) -> b.length() - a.length());

        for (String candidate : candidates) {
            File target = new File(candidate.trim());
            if (target.exists()) return target;
        }
        return null;
    }

    private static void collectPathMatches(String text, List<String> out) {
        Matcher m = WINDOWS_PATH.matcher(text);
        while (m.find()) {
            String candidate = m.group();
            if (candidate.length() > 3) out.add(candidate);
        }
    }

    // ---- Linux .desktop entries ---------------------------------------------

    private static File resolveDesktopEntryFile(File desktopFile) throws IOException {
        String url = readIniValue(desktopFile, "URL");
        if (url != null) {
            String path = url.startsWith("file://") ? url.substring("file://".length()) : url;
            File target = new File(path);
            if (target.exists()) return target;
        }
        String exec = readIniValue(desktopFile, "Exec");
        if (exec != null && !exec.isEmpty()) {
            String bin = exec.trim().split("\\s+")[0];
            File target = new File(bin);
            if (target.exists()) return target;
        }
        return null;
    }

    /** Reads the first "Key=value" line matching the given key from a small INI-style text file. */
    private static String readIniValue(File file, String key) throws IOException {
        String prefix = key + "=";
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
