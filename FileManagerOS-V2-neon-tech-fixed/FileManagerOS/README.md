# File Manager — Operating Systems Project

A desktop file manager built in **Java (Swing)** with a minimalist,
**midnight-themed** dark GUI. It talks directly to the host operating
system's filesystem — listing directories, creating/renaming/deleting
files and folders, copying and moving data, and launching files with the
OS's own default application — which is the "interacts with the operating
system" requirement for this assignment.

No external libraries are used. Everything runs on the standard JDK
(`java.io`, `java.nio.file`, `java.awt.Desktop`, and Swing), so it opens
and runs in VS Code with nothing to install beyond a JDK.

---

## Features

| Category | What it does |
|---|---|
| **Browse** | Navigate folders, back/forward history, "up one level", refresh, drive/root list, quick-access shortcuts (Home, Desktop, Documents, Downloads, Pictures) |
| **Address bar** | Type or paste any path and press Enter to jump straight there |
| **Create** | New Folder, New File (toolbar, right-click menu, or keyboard) |
| **Rename** | Toolbar button, right-click, or **F2** |
| **Delete** | Toolbar, right-click, or **Delete** key — sends items to the OS recycle bin / trash (recoverable). Hold **Shift** (Shift+Delete, or Shift-click the toolbar Delete button) to permanently delete instead, with a confirmation prompt |
| **Undo delete (Ctrl+Z)** | Restores the most recently trashed item(s) back to their original location — toolbar button or **Ctrl+Z**. Best-effort and OS-dependent (see "Platform limitations" below); reports clearly if it couldn't confirm a restore rather than pretending it worked |
| **Drag and drop** | Drag files out of the table onto the OS desktop or another app to copy (or move, if the target does the move) them there; drag files in from the OS, or drag an item onto a folder row within the app, to copy/move them in. Guarded against the two ways this can go wrong: dragging a folder into its own subfolder is rejected outright, and a folder containing a symlink cycle is copied without infinitely recursing into itself |
| **Copy / Cut / Paste** | Toolbar, right-click, or **Ctrl+C / Ctrl+X / Ctrl+V** — works across folders, auto-renames on collision (`name (copy)`) |
| **Open** | Double-click (or Enter) opens a file with the operating system's default associated application, or navigates into a folder |
| **Shortcuts & links** | Windows `.lnk` shortcuts, `.url` internet shortcuts, Linux `.desktop` entries, and symbolic links are detected, badged with a small arrow overlay, and resolved to their real target — a shortcut to a folder navigates in, a shortcut to a file opens it, and a web-link shortcut opens in the default browser |
| **Search** | Type in the search box to recursively search the current folder and its subfolders by name. Uses the OS's own search index (Spotlight on macOS, locate/plocate on Linux) when one is available, falling back everywhere — and always on Windows — to a parallel, multi-threaded directory walk |
| **Name validation** | New folder/file names and renames are checked before touching the filesystem — blocks empty names, illegal characters, path separators (so a rename can't escape the current folder), reserved Windows device names (`CON`, `NUL`, etc.), and names ending in a dot or space |
| **Sort** | Click any column header (Name / Size / Type / Date Modified) to sort; click again to reverse. Folders always stay grouped above files |
| **Properties** | Right-click → Properties shows location, type, created/modified dates, and a recursively-computed size (calculated on a background thread so the UI never freezes), plus a read-only toggle |
| **Hidden files** | Toggle "Show hidden" in the toolbar |
| **Status bar** | Live item count, current selection count/size, and clipboard status |

## How it interacts with the OS

All filesystem access is centralized in `filemanager.core.FileOperations`,
which wraps:

- `java.nio.file.Files` — directory listing, copy, move, delete, attribute reads (size, timestamps)
- `java.io.File` — path resolution, filesystem roots (`File.listRoots()` — this is how the sidebar finds every mounted drive on the OS)
- `java.awt.Desktop` — asking the **host OS** to open a file with whatever application it's associated with (e.g. opening a `.txt` in Notepad/TextEdit, an image in the system viewer), and revealing a file in the native file browser

Opening a file is layered rather than a single point of failure: it tries
`java.awt.Desktop` first, and if that's unsupported (common on some Linux
setups) or refuses the file, it falls back to asking the OS shell directly
(`cmd /c start` on Windows, `open` on macOS, `xdg-open` on Linux) — the same
mechanism a native double-click uses. `filemanager.core.ShortcutResolver`
adds understanding of Windows `.lnk` shortcuts, `.url` internet shortcuts,
and Linux `.desktop` entries, so a shortcut opens whatever it *points to*
rather than being treated as an opaque, unopenable file.

Nothing is faked or simulated — every action in the GUI performs a real
operation against the actual filesystem you run it on.

## Platform limitations (worth knowing, and worth mentioning in a report)

Two features here are honestly **best-effort**, not guaranteed, because the
JDK doesn't expose a clean cross-platform API for either of them:

- **Undo delete (Ctrl+Z)** restores a trashed item by going back into the OS
  trash and finding it — `java.awt.Desktop` can send a file *to* the trash
  but gives no way back. `filemanager.core.TrashManager` implements this
  per-OS: reliably on Linux (the freedesktop.org Trash spec stores each
  item's original path in a sidecar file, so the lookup is exact); by
  best-effort name-matching on macOS (`~/.Trash` has no such metadata); and
  via PowerShell + the `Shell.Application` COM object on Windows (can only
  match by display name, and the restore verb isn't guaranteed on every
  Windows build/locale). If a restore can't be confirmed, the app says so
  explicitly rather than assuming it worked — check the recycle bin/trash
  manually in that case.
- **Search** uses the OS's own indexed search (`mdfind` on macOS,
  `locate`/`plocate` on Linux) when available, since that's the only way to
  approach Explorer/Finder-level speed — a live directory walk is a
  fundamentally slower category of work than an indexed lookup, no matter
  how it's optimized. Windows Search's index is only reachable through a
  fragile, locale-sensitive COM/ADO API, so this app doesn't attempt it;
  Windows always uses the parallel directory-walk fallback instead (see
  `FileSearchService`), which is slower than an index but fully portable
  and reliable.

## Architecture

```
src/filemanager/
├── Main.java                  entry point
├── core/                      OS interaction layer (no Swing code here)
│   ├── FileEntry.java          one row's worth of pre-computed file data
│   ├── FileOperations.java     all filesystem reads/writes
│   ├── FileNameValidator.java  rejects invalid/unsafe names before they touch disk
│   ├── ShortcutResolver.java   resolves .lnk / .url / .desktop shortcuts
│   ├── FileSearchService.java  indexed-search fast path + parallel walk fallback
│   ├── TrashManager.java       moveToTrash() + best-effort, per-OS restore-from-trash
│   ├── TrashRecord.java        "where a file was before it was trashed" record
│   ├── DeleteHistory.java      bounded undo stack of trash-delete batches
│   └── Clipboard.java          internal copy/cut state
├── ui/                         Swing presentation layer
│   ├── Theme.java               midnight color palette + shared styling
│   ├── MainFrame.java           top-level window; wires everything together
│   ├── Toolbar.java             nav buttons, address bar, search, actions
│   ├── SidebarPanel.java        quick access + drives
│   ├── FileTablePanel.java      the file list (table, context menu, shortcuts, drag-and-drop)
│   ├── FileTableModel.java      table data + sorting
│   ├── IconProvider.java        hand-drawn minimalist folder/file icons
│   ├── PlaceholderTextField.java  text field with greyed-out hint text
│   └── StatusBar.java           footer status line
└── dialogs/
    ├── InputDialog.java         reusable rename/new-item prompt
    └── PropertiesDialog.java    file/folder properties panel
```

The `core` package never imports Swing — it could be reused by a
completely different UI. The `ui` package only ever talks to the
filesystem through `core.FileOperations`, keeping "OS logic" and
"presentation" cleanly separated, which is worth calling out in your
project write-up.

---

## Running it in VS Code

1. Install the **"Extension Pack for Java"** (Microsoft) from the VS Code marketplace if you don't already have it — it bundles the Java language server, debugger, and test runner.
2. Open this folder (`FileManagerOS/`) in VS Code: **File → Open Folder…**
3. Open `src/filemanager/Main.java`. VS Code should show a **▶ Run** button above `public static void main`. Click it (or press `F5`).
4. The `.vscode/launch.json` in this project is already configured with the main class `filemanager.Main`, so Run/Debug work immediately.

### Running from a terminal instead

```bash
# from the project root
mkdir -p bin
javac -d bin $(find src -name "*.java")
java -cp bin filemanager.Main
```

Requires JDK 17 or later (uses no version-specific features beyond basic
modern Java, so anything from JDK 11+ will also compile it).

---

## Notes for your report

- **Language/IDE:** Java (Swing, JDK standard library only), written and run in Visual Studio Code with the Java Extension Pack.
- **OS interaction points:** filesystem CRUD via `java.nio.file`, drive/root enumeration via `File.listRoots()`, delegating to the OS's default application via `java.awt.Desktop.open()`, sending files to the OS recycle bin/trash via `Desktop.moveToTrash()` (Java 9+), and native drag-and-drop in/out via Swing's `TransferHandler` with `DataFlavor.javaFileListFlavor`.
- **Concurrency:** recursive folder-size calculation (Properties dialog) and recursive search both run on a background thread (`SwingWorker`) so the GUI stays responsive — a good talking point if your rubric touches on OS concepts like threads/processes.
- **Theme:** all colors are centralized in `ui/Theme.java` — one place to tweak the whole look.
