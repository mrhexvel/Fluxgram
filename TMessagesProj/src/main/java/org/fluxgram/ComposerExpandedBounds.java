package org.fluxgram;

/** Immutable field-local pixel bounds available to an expanded draw callback. */
public final class ComposerExpandedBounds {
    /** Maximum requested outset from each field edge, in dp, before host-container clipping. */
    public static final int MAX_OUTSET_DP = 128;

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    /** Creates pixel bounds relative to the field origin; left or top may be negative. */
    public ComposerExpandedBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
