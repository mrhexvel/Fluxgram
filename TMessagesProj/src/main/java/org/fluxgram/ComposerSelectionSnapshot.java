package org.fluxgram;

public final class ComposerSelectionSnapshot {
    public final long revision;
    public final int start;
    public final int end;
    public final int lineCount;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

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
