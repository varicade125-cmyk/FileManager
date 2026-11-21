package filemanager.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Bounded history of trash-delete batches (one batch = one Delete action,
 * which may have covered several selected files at once), so Ctrl+Z can
 * undo the most recent one. Capped so a long session doesn't quietly grow
 * this without bound.
 */
public final class DeleteHistory {

    private static final int MAX_BATCHES = 25;

    private final Deque<List<TrashRecord>> batches = new ArrayDeque<>();

    public void push(List<TrashRecord> batch) {
        if (batch == null || batch.isEmpty()) return;
        batches.push(batch);
        while (batches.size() > MAX_BATCHES) {
            batches.removeLast();
        }
    }

    public boolean isEmpty() {
        return batches.isEmpty();
    }

    /** Removes and returns the most recent batch. Throws if empty — check isEmpty() first. */
    public List<TrashRecord> pop() {
        return batches.pop();
    }
}
