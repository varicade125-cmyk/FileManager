package filemanager.core;

import java.io.File;

/**
 * Records where a file or folder lived immediately before it was sent to the
 * OS recycle bin / trash, so Ctrl+Z has something to restore it to. Created
 * the moment a delete succeeds — never reconstructed after the fact — since
 * the original location is exactly the one piece of information the OS
 * trash APIs don't hand back to us.
 */
public final class TrashRecord {

    public final File originalLocation;
    public final long deletedAtMillis;
    public final boolean directory;

    public TrashRecord(File originalLocation, long deletedAtMillis, boolean directory) {
        this.originalLocation = originalLocation;
        this.deletedAtMillis = deletedAtMillis;
        this.directory = directory;
    }
}
