package org.fluxgram;

/**
 * Immutable before/after layout change tied to one text revision.
 * Diff offsets and counts are UTF-16 units; {@link #removedCapture} is optional and host-owned.
 */
public final class ComposerTextLayoutChange {
    public final long revision;
    public final ComposerTextLayoutSnapshot before;
    public final ComposerTextLayoutSnapshot after;
    public final int start;
    public final int removedCount;
    public final int insertedCount;
    public final boolean coalesced;
    public final ComposerTextCapture removedCapture;

    /** Creates a layout change without a detached removed-text capture. */
    public ComposerTextLayoutChange(long revision,
            ComposerTextLayoutSnapshot before, ComposerTextLayoutSnapshot after,
            int start, int removedCount, int insertedCount, boolean coalesced) {
        this(revision, before, after, start, removedCount, insertedCount, coalesced, null);
    }

    /**
     * Creates a layout change with an optional callback-scoped removed-text capture. Such a
     * capture is available only for a reliable, non-coalesced, bounded removal or replacement.
     */
    public ComposerTextLayoutChange(long revision,
            ComposerTextLayoutSnapshot before, ComposerTextLayoutSnapshot after,
            int start, int removedCount, int insertedCount, boolean coalesced,
            ComposerTextCapture removedCapture) {
        this.revision = revision;
        this.before = before;
        this.after = after;
        this.start = start;
        this.removedCount = removedCount;
        this.insertedCount = insertedCount;
        this.coalesced = coalesced;
        this.removedCapture = removedCapture;
    }
}
