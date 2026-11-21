package filemanager.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the files currently staged for a copy or cut operation, along with
 * whether it was a "cut" (move) or a "copy". This is the app's own internal
 * clipboard, independent of the system clipboard.
 */
public class Clipboard {

    public enum Mode { NONE, COPY, CUT }

    private List<File> files = new ArrayList<>();
    private Mode mode = Mode.NONE;

    public void setCopy(List<File> files) {
        this.files = new ArrayList<>(files);
        this.mode = Mode.COPY;
    }

    public void setCut(List<File> files) {
        this.files = new ArrayList<>(files);
        this.mode = Mode.CUT;
    }

    public void clear() {
        files.clear();
        mode = Mode.NONE;
    }

    public boolean isEmpty() {
        return mode == Mode.NONE || files.isEmpty();
    }

    public boolean isCut() {
        return mode == Mode.CUT;
    }

    public List<File> getFiles() {
        return Collections.unmodifiableList(files);
    }
}
