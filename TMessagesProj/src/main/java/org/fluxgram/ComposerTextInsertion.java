package org.fluxgram;

/** Immutable insertion mapped exactly to a contiguous range of final-layout shaping clusters. */
public final class ComposerTextInsertion {
    /** Maximum cluster count delivered for one insertion. */
    public static final int MAX_CLUSTERS = 64;

    public final long revision;
    public final ComposerTextLayoutSnapshot layout;
    public final int start;
    public final int removedCount;
    public final int insertedCount;
    public final boolean coalesced;
    public final int firstCluster;
    public final int clusterCount;

    /**
     * Creates an insertion. Text offsets/counts are UTF-16 units; {@code firstCluster} and
     * {@code clusterCount} index {@code layout}'s cluster array and cover the full inserted range.
     */
    public ComposerTextInsertion(long revision, ComposerTextLayoutSnapshot layout,
            int start, int removedCount, int insertedCount, boolean coalesced,
            int firstCluster, int clusterCount) {
        this.revision = revision;
        this.layout = layout;
        this.start = start;
        this.removedCount = removedCount;
        this.insertedCount = insertedCount;
        this.coalesced = coalesced;
        this.firstCluster = firstCluster;
        this.clusterCount = clusterCount;
    }
}
