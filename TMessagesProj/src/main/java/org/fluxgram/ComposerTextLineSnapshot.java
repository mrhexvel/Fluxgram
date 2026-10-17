package org.fluxgram;

public final class ComposerTextLineSnapshot {
    public final int start;
    public final int end;
    public final int visibleEnd;
    public final float left;
    public final float right;
    public final float top;
    public final float bottom;
    public final float baseline;
    public final boolean rtl;

    public ComposerTextLineSnapshot(int start, int end, int visibleEnd,
            float left, float right, float top, float bottom, float baseline, boolean rtl) {
        this.start = start;
        this.end = end;
        this.visibleEnd = visibleEnd;
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        this.baseline = baseline;
        this.rtl = rtl;
    }
}
