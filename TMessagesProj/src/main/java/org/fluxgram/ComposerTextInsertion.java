package org.fluxgram;

public final class ComposerTextInsertion {
    public static final int MAX_CLUSTERS = 64;

    public final long revision;
    public final ComposerTextLayoutSnapshot layout;
    public final int start;
    public final int removedCount;
    public final int insertedCount;
    public final boolean coalesced;
    public final int firstCluster;
    public final int clusterCount;

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
