package org.fluxgram;

/** Immutable cursor geometry for one draw, expressed in field-local physical pixels. */
public final class ComposerCursorSnapshot {
    public final long revision;
    public final int selectionOffset;
    public final int line;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    public final float baseline;
    public final boolean rtl;

    /**
     * Creates a snapshot. The selection offset is a UTF-16 offset; bounds and baseline already
     * include the field's current vertical scroll and animated text offset.
     */
    public ComposerCursorSnapshot(long revision, int selectionOffset, int line,
            float left, float top, float right, float bottom, float baseline, boolean rtl) {
        this.revision = revision;
        this.selectionOffset = selectionOffset;
        this.line = line;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.baseline = baseline;
        this.rtl = rtl;
    }
}
