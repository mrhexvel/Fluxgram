package org.telegram.messenger.plugins.api;

public final class ComposerExpandedBounds {
    public static final int MAX_OUTSET_DP = 128;

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public ComposerExpandedBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
