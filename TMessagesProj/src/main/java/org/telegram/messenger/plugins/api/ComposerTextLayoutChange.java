package org.telegram.messenger.plugins.api;

public final class ComposerTextLayoutChange {
    public final long revision;
    public final ComposerTextLayoutSnapshot before;
    public final ComposerTextLayoutSnapshot after;
    public final int start;
    public final int removedCount;
    public final int insertedCount;
    public final boolean coalesced;
    public final ComposerTextCapture removedCapture;

    public ComposerTextLayoutChange(long revision,
            ComposerTextLayoutSnapshot before, ComposerTextLayoutSnapshot after,
            int start, int removedCount, int insertedCount, boolean coalesced) {
        this(revision, before, after, start, removedCount, insertedCount, coalesced, null);
    }

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
