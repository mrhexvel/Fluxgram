package org.fluxgram;

/** Immutable directional text run linking a UTF-16 range to shaping clusters. */
public final class ComposerTextRunSnapshot {
    public final int start;
    public final int end;
    public final int line;
    public final boolean rtl;
    public final int clusterStart;
    public final int clusterCount;

    /**
     * Creates a run. {@code clusterStart} and {@code clusterCount} index the enclosing layout
     * snapshot's bounded cluster array.
     */
    public ComposerTextRunSnapshot(
            int start, int end, int line, boolean rtl, int clusterStart, int clusterCount) {
        this.start = start;
        this.end = end;
        this.line = line;
        this.rtl = rtl;
        this.clusterStart = clusterStart;
        this.clusterCount = clusterCount;
    }
}
