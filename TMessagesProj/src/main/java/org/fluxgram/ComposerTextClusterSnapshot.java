package org.fluxgram;

/**
 * Immutable caret/shaping cluster produced by the current Android text shaper.
 * It is not a glyph ID, outline, or cross-platform Unicode segmentation guarantee.
 */
public final class ComposerTextClusterSnapshot {
    public final int start;
    public final int end;
    public final int line;
    public final boolean rtl;
    public final float primaryStart;
    public final float primaryEnd;
    public final float secondaryStart;
    public final float secondaryEnd;
    public final float advance;

    /**
     * Creates a cluster. Offsets are UTF-16, {@code line} indexes captured visible lines, and all
     * horizontal positions and advance values are field-local physical pixels.
     */
    public ComposerTextClusterSnapshot(int start, int end, int line, boolean rtl,
            float primaryStart, float primaryEnd, float secondaryStart, float secondaryEnd, float advance) {
        this.start = start;
        this.end = end;
        this.line = line;
        this.rtl = rtl;
        this.primaryStart = primaryStart;
        this.primaryEnd = primaryEnd;
        this.secondaryStart = secondaryStart;
        this.secondaryEnd = secondaryEnd;
        this.advance = advance;
    }
}
