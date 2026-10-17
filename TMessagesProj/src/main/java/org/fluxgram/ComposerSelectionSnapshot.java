package org.fluxgram;

/** Immutable bounded selection-mask geometry in field-local physical pixels. */
public final class ComposerSelectionSnapshot {
    public final long revision;
    public final int start;
    public final int end;
    public final int lineCount;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    /**
     * Creates a snapshot for a non-empty UTF-16 range spanning at most 16 visible lines.
     * Bounds are the clipped host capture and are tied to {@code revision}.
     */
    public ComposerSelectionSnapshot(long revision, int start, int end, int lineCount,
            float left, float top, float right, float bottom) {
        this.revision = revision;
        this.start = start;
        this.end = end;
        this.lineCount = lineCount;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
