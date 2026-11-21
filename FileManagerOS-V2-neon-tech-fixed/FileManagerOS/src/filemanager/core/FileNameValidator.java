package filemanager.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates a proposed file/folder name before it's ever handed to the
 * filesystem. Deliberately conservative: it enforces the union of what's
 * invalid on Windows, macOS, and Linux (not just whatever the current OS
 * happens to allow) so a name typed on one machine won't break if the
 * folder is ever moved to another, and so a name can't be used to smuggle
 * a path separator into what's supposed to be a single file/folder name.
 */
public final class FileNameValidator {

    private FileNameValidator() { }

    /** Characters that are illegal in a filename on at least one major OS. */
    private static final String FORBIDDEN_CHARS = "\\/:*?\"<>|";

    private static final int MAX_NAME_LENGTH = 255;

    private static final Set<String> RESERVED_WINDOWS_NAMES = new HashSet<>(Arrays.asList(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"));

    /**
     * Checks a proposed name and returns a short, human-readable reason it's
     * invalid — or null if the name is fine to use.
     */
    public static String validate(String name) {
        if (name == null) return "Name cannot be empty.";
        String trimmed = name.trim();

        if (trimmed.isEmpty()) return "Name cannot be empty.";
        if (trimmed.equals(".") || trimmed.equals("..")) return "\"" + trimmed + "\" isn't a valid name.";
        if (trimmed.length() > MAX_NAME_LENGTH) return "Name is too long (max " + MAX_NAME_LENGTH + " characters).";

        // Blocks both illegal characters AND directory separators — the
        // latter is what stops a name like "../../secret" from escaping the
        // folder the operation is supposed to be confined to.
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (FORBIDDEN_CHARS.indexOf(c) >= 0) {
                return "Name can't contain any of: " + FORBIDDEN_CHARS;
            }
            if (c < 0x20) {
                return "Name can't contain control characters.";
            }
        }

        if (trimmed.endsWith(".") || trimmed.endsWith(" ")) {
            return "Name can't end with a dot or a space.";
        }

        String base = trimmed.contains(".") ? trimmed.substring(0, trimmed.indexOf('.')) : trimmed;
        if (RESERVED_WINDOWS_NAMES.contains(base.toUpperCase())) {
            return "\"" + base.toUpperCase() + "\" is a reserved system name.";
        }

        return null;
    }

    public static boolean isValid(String name) {
        return validate(name) == null;
    }
}
