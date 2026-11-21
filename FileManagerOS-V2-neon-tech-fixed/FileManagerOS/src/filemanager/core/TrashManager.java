package filemanager.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Restoring something out of the OS recycle bin/trash back to where it came
 * from is, honestly, not something the JDK gives a clean, cross-platform way
 * to do — java.awt.Desktop can send a file *to* the trash (moveToTrash, used
 * elsewhere in this app) but hands back no way to reverse that. So this is
 * a best-effort, OS-specific implementation, and it says so out loud when it
 * can't confirm success rather than silently pretending it worked:
 *
 *  - Linux: the freedesktop.org Trash spec is well-documented and stable —
 *    every trashed item gets a sidecar ".trashinfo" file recording its
 *    original path, so restoring is a straightforward, reliable lookup.
 *  - macOS: ~/.Trash is just a flat folder with no structured metadata
 *    about where a file came from, so this falls back to matching by name
 *    (including Finder's "name 2", "name 3" collision suffixes) and picking
 *    whichever candidate was trashed closest to the recorded deletion time.
 *  - Windows: the Recycle Bin's real contents are only reachable through
 *    the Shell.Application COM object, which this drives via a small
 *    PowerShell script. It can only match by display name (the property
 *    that reliably records the *original* full path is exposed under a
 *    locale-dependent column name), so — like macOS — this is best-effort,
 *    not guaranteed.
 */
public final class TrashManager {

    private TrashManager() { }

    /** Sends a file/folder to the OS trash. Returns false (not an exception) if unsupported. */
    public static boolean moveToTrash(File target) {
        return FileOperations.moveToTrash(target);
    }

    /**
     * Attempts to move a previously-trashed item back to its original
     * location. Returns false if it couldn't be found or restored — callers
     * should surface that rather than assume success.
     */
    public static boolean restore(TrashRecord record) {
        if (record.originalLocation.exists()) return false; // don't clobber something already there
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("nux") || os.contains("nix")) return restoreLinux(record);
            if (os.contains("mac")) return restoreMac(record);
            if (os.contains("win")) return restoreWindows(record);
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    // ---- Linux: freedesktop.org Trash spec (reliable) ----------------------

    private static boolean restoreLinux(TrashRecord record) throws IOException {
        File home = new File(System.getProperty("user.home"));
        File infoDir = new File(home, ".local/share/Trash/info");
        File filesDir = new File(home, ".local/share/Trash/files");
        if (!infoDir.isDirectory() || !filesDir.isDirectory()) return false;

        File[] infoFiles = infoDir.listFiles((d, name) -> name.endsWith(".trashinfo"));
        if (infoFiles == null) return false;

        String targetPath = record.originalLocation.getAbsolutePath();
        File bestMatch = null;
        long bestTime = -1;

        for (File info : infoFiles) {
            String path = null;
            for (String line : Files.readAllLines(info.toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("Path=")) {
                    path = decodeTrashPath(line.substring("Path=".length()));
                    break;
                }
            }
            if (path != null && path.equals(targetPath) && info.lastModified() > bestTime) {
                bestTime = info.lastModified();
                bestMatch = info;
            }
        }
        if (bestMatch == null) return false;

        String infoName = bestMatch.getName();
        String baseName = infoName.substring(0, infoName.length() - ".trashinfo".length());
        File trashedFile = new File(filesDir, baseName);
        if (!trashedFile.exists()) return false;

        File parent = record.originalLocation.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Files.move(trashedFile.toPath(), record.originalLocation.toPath());
        bestMatch.delete();
        return true;
    }

    private static String decodeTrashPath(String uriEncoded) {
        try {
            // .trashinfo uses plain percent-encoding, where '+' is a literal plus sign.
            // URLDecoder is for form encoding and would turn it into a space, so
            // "C++ notes.txt" could never be matched and Undo failed for such names.
            return URLDecoder.decode(uriEncoded.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return uriEncoded;
        }
    }

    // ---- macOS: flat ~/.Trash folder, matched by name (best-effort) -------

    private static boolean restoreMac(TrashRecord record) throws IOException {
        File trash = new File(System.getProperty("user.home"), ".Trash");
        if (!trash.isDirectory()) return false;

        String originalName = record.originalLocation.getName();
        String base = stripExtension(originalName);
        String ext = extensionOf(originalName);
        // Finder appends " 2", " 3", ... to the *base* name (before the
        // extension) when something with the same name already exists.
        Pattern collisionPattern = Pattern.compile(
                Pattern.quote(base) + "( \\d+)?" + (ext.isEmpty() ? "" : Pattern.quote("." + ext)));

        File[] candidates = trash.listFiles();
        if (candidates == null) return false;

        File best = null;
        long bestDiff = Long.MAX_VALUE;
        for (File c : candidates) {
            if (!collisionPattern.matcher(c.getName()).matches()) continue;
            long diff = Math.abs(c.lastModified() - record.deletedAtMillis);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = c;
            }
        }
        if (best == null) return false;

        File parent = record.originalLocation.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Files.move(best.toPath(), record.originalLocation.toPath());
        return true;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    // ---- Windows: Shell.Application COM automation via PowerShell (best-effort) ----

    private static boolean restoreWindows(TrashRecord record) throws IOException, InterruptedException {
        String name = record.originalLocation.getName().replace("'", "''");
        String script =
                "$ErrorActionPreference = 'Stop';" +
                "$shell = New-Object -ComObject Shell.Application;" +
                "$bin = $shell.Namespace(10);" +
                "$match = $null;" +
                "foreach ($item in $bin.Items()) {" +
                "  if ($item.Name -eq '" + name + "') { $match = $item; break; }" +
                "}" +
                "if ($match -ne $null) {" +
                "  $match.InvokeVerb('undelete');" +
                "  Write-Output 'OK';" +
                "} else {" +
                "  Write-Output 'NOTFOUND';" +
                "}";
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Wait (with the timeout) BEFORE reading the output. The old order read the
        // stream to EOF first, which blocks until the process exits, so a hung
        // PowerShell froze the caller forever and the 8s timeout never applied.
        // The script only prints "OK" / "NOTFOUND" (or one short error), so the
        // pipe buffer can't fill up while we wait.
        boolean finished = process.waitFor(8, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return false;
        }
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        // Best-effort: this can only match by display name (multiple items
        // trashed with the same name can't be told apart), and the
        // 'undelete' verb isn't guaranteed present on every Windows build/
        // locale. A confirmed "OK" is trustworthy; anything else is treated
        // as failure rather than assumed to have worked.
        return output.contains("OK");
    }
}
