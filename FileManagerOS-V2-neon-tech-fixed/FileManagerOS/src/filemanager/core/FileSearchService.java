package filemanager.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Why the built-in search is slower than Explorer/Finder/Nautilus's search
 * box, and what this class does about it:
 *
 * Real OS file managers almost never search live. Windows Search, Spotlight
 * (macOS), and Tracker/plocate (Linux) all maintain a background *index* of
 * the filesystem ahead of time, so a search is really just a lookup against
 * that index — O(matches), not O(files on disk). This app has no such index,
 * so a search has to actually walk the directory tree at the moment you ask
 * for it. That's a fundamentally different, slower order of work, and no
 * amount of micro-optimizing the walk itself changes that fact.
 *
 * What we *can* do:
 *  1. Where the OS exposes its own index via a simple, safe command-line
 *     tool, use that instead of walking ourselves — this is what actually
 *     closes the gap, since it hands the work to the same index Explorer/
 *     Finder use. Implemented here for macOS (mdfind / Spotlight) and Linux
 *     (locate/plocate). Windows Search's index is only reachable through a
 *     COM/ADO API that's fragile and locale-sensitive to drive from a
 *     command line without extra tooling, so it's deliberately left out
 *     here rather than shipping something unreliable — see search() below.
 *  2. Where we do have to walk the tree ourselves (every OS, as the
 *     fallback; the only path at all on Windows), do it with a thread pool
 *     instead of single-threaded recursion. Directory scanning is I/O-bound,
 *     so multiple subdirectories can genuinely be read in parallel — a real,
 *     portable speedup fully within our own control, unlike the fragile
 *     native-index route.
 */
public final class FileSearchService {

    private FileSearchService() { }

    private static final int NATIVE_SEARCH_TIMEOUT_SECONDS = 3;
    private static final int COMMAND_PROBE_TIMEOUT_SECONDS = 1;

    /**
     * Searches for entries under {@code root} whose name contains
     * {@code query} (case-insensitive), stopping at {@code limit} results.
     * {@code cancelled} is polled periodically so an in-progress search can
     * be abandoned early (e.g. the user typed a new query, or navigated
     * away) without waiting for the whole walk to unwind.
     */
    public static List<FileEntry> search(File root, String query, boolean showHidden, int limit, BooleanSupplier cancelled) {
        List<FileEntry> indexed = tryNativeIndexedSearch(root, query, showHidden, limit, cancelled);
        // An EMPTY indexed result is treated as "inconclusive", not "no matches": a
        // locate/Spotlight index can be stale (files created since the last
        // updatedb) or never built at all, and in both cases it answers with an
        // empty list rather than an error. Only a non-empty answer is trusted;
        // otherwise fall through to the live walk, which is always correct.
        if (indexed != null && !indexed.isEmpty()) return indexed;
        return parallelWalk(root, query.toLowerCase(), showHidden, limit, cancelled);
    }

    // ---- Fast path: hand off to the OS's own search index, where we can reach one safely ----

    private static List<FileEntry> tryNativeIndexedSearch(File root, String query, boolean showHidden,
                                                            int limit, BooleanSupplier cancelled) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) return spotlightSearch(root, query, showHidden, limit, cancelled);
            if (os.contains("nux") || os.contains("nix")) return locateSearch(root, query, showHidden, limit, cancelled);
        } catch (Exception ignored) {
            // Any failure here just means "no fast path available right now" — the
            // caller transparently falls back to the parallel walk, so a missing
            // tool, a stale index, or a permissions hiccup never breaks search.
        }
        return null;
    }

    private static List<FileEntry> spotlightSearch(File root, String query, boolean showHidden,
                                                     int limit, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        if (!commandExists("mdfind")) return null;
        ProcessBuilder pb = new ProcessBuilder("mdfind", "-onlyin", root.getAbsolutePath(),
                "kMDItemFSName == '*" + query.replace("'", "") + "*'c");
        return runAndCollect(pb, showHidden, limit, cancelled);
    }

    private static List<FileEntry> locateSearch(File root, String query, boolean showHidden,
                                                  int limit, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        String binary = commandExists("plocate") ? "plocate" : (commandExists("locate") ? "locate" : null);
        if (binary == null) return null;

        // locate/plocate search the *whole* indexed filesystem — there's no
        // "only under this folder" flag — so ask for a generous batch and
        // filter down to the current folder ourselves.
        // "-b" = match the file NAME only. Without it locate matches the whole path,
        // so searching "report" also returned every file merely *inside* a folder
        // called "report", unlike the name-only walk fallback.
        ProcessBuilder pb = new ProcessBuilder(binary, "-i", "-b", query);
        List<FileEntry> all = runAndCollect(pb, showHidden, 5000, cancelled);
        if (all == null) return null;

        // Compare against "<root>/" rather than "<root>": a bare prefix check let
        // sibling folders leak in (searching in /x/Doc also matched /x/Documents/...).
        String prefix = root.getAbsolutePath();
        if (!prefix.endsWith(File.separator)) prefix += File.separator;
        String needleLower = query.toLowerCase();

        List<FileEntry> scoped = new ArrayList<>();
        for (FileEntry e : all) {
            if (cancelled.getAsBoolean()) break;
            File f = e.getFile();
            if (!f.getAbsolutePath().startsWith(prefix)) continue;
            // Same literal "name contains query" rule as the walk fallback (locate
            // would otherwise treat * ? [ in the query as glob patterns).
            if (!f.getName().toLowerCase().contains(needleLower)) continue;
            if (!showHidden && isInsideHiddenFolder(f, root)) continue;
            scoped.add(e);
            if (scoped.size() >= limit) break;
        }
        return scoped;
    }

    /** True if any folder between {@code root} and {@code f} is hidden. The parallel walk never
     *  descends into hidden folders; this keeps the locate path consistent with that. */
    private static boolean isInsideHiddenFolder(File f, File root) {
        for (File p = f.getParentFile(); p != null && !p.equals(root); p = p.getParentFile()) {
            if (p.isHidden()) return true;
        }
        return false;
    }

    private static boolean commandExists(String command) {
        try {
            Process p = new ProcessBuilder("which", command).redirectErrorStream(true).start();
            boolean finished = p.waitFor(COMMAND_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<FileEntry> runAndCollect(ProcessBuilder pb, boolean showHidden, int limit,
                                                   BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        Process process = pb.start();

        // readLine() below blocks, so it can't check "cancelled" between lines
        // on its own — this watcher kills the process the moment cancellation
        // is requested, which unblocks the read loop immediately instead of
        // leaving Cancel do nothing until the command finishes by itself.
        Thread cancelWatcher = new Thread(() -> {
            while (process.isAlive()) {
                if (cancelled.getAsBoolean()) {
                    process.destroyForcibly();
                    return;
                }
                try { Thread.sleep(40); } catch (InterruptedException e) { return; }
            }
        });
        cancelWatcher.setDaemon(true);
        cancelWatcher.start();

        List<FileEntry> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (results.size() >= limit || cancelled.getAsBoolean()) break;
                File f = new File(line.trim());
                if (!f.exists()) continue;
                if (!showHidden && f.isHidden()) continue;
                results.add(new FileEntry(f));
            }
        } finally {
            cancelWatcher.interrupt();
        }

        if (cancelled.getAsBoolean()) {
            process.destroyForcibly();
            return results; // partial results are fine — the caller is about to discard them anyway
        }

        boolean finished = process.waitFor(NATIVE_SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null; // A hang/timeout is treated as "not usable" — fall back rather than block the UI.
        }
        return results;
    }

    // ---- Fallback: portable, parallel directory walk (the only path on Windows, and the
    //      fallback everywhere else if the native tool above isn't installed/usable) --------

    private static List<FileEntry> parallelWalk(File root, String needleLower, boolean showHidden,
                                                  int limit, BooleanSupplier cancelled) {
        List<FileEntry> results = new CopyOnWriteArrayList<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger pendingTasks = new AtomicInteger(0);
        try {
            submitWalk(pool, root, needleLower, showHidden, limit, results, cancelled, visited, pendingTasks);
            // Every submitWalk() call increments pendingTasks before it queues the
            // task, and decrements only after that task (including everything it
            // itself submitted) has finished — so this can't drop to zero while
            // there's still unfinished or unsubmitted work.
            while (pendingTasks.get() > 0 && results.size() < limit && !cancelled.getAsBoolean()) {
                try { Thread.sleep(15); } catch (InterruptedException ignored) { break; }
            }
        } finally {
            pool.shutdownNow();
        }
        return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : new ArrayList<>(results);
    }

    private static void submitWalk(ExecutorService pool, File dir, String needleLower, boolean showHidden,
                                    int limit, List<FileEntry> results, BooleanSupplier cancelled,
                                    Set<String> visited, AtomicInteger pendingTasks) {
        pendingTasks.incrementAndGet();
        pool.submit(() -> {
            try {
                if (cancelled.getAsBoolean() || results.size() >= limit) return;

                // Same cycle guard as a single-threaded walk needs: a symlink or
                // Windows junction pointing back at an ancestor folder would
                // otherwise cause this to recurse (here: keep re-submitting tasks)
                // forever.
                String canonical;
                try {
                    canonical = dir.getCanonicalPath();
                } catch (IOException e) {
                    return; // broken/dangling link - skip it
                }
                if (!visited.add(canonical)) return;

                File[] children = dir.listFiles();
                if (children == null) return; // permission denied - skip, don't fail the whole search
                for (File f : children) {
                    if (cancelled.getAsBoolean() || results.size() >= limit) return;
                    if (!showHidden && f.isHidden()) continue;
                    if (f.getName().toLowerCase().contains(needleLower)) {
                        results.add(new FileEntry(f));
                    }
                    if (f.isDirectory()) {
                        submitWalk(pool, f, needleLower, showHidden, limit, results, cancelled, visited, pendingTasks);
                    }
                }
            } finally {
                pendingTasks.decrementAndGet();
            }
        });
    }
}
