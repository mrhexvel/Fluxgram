package org.telegram.messenger.plugins.api;

public final class ComposerTextRunSnapshot {
    public final int start;
    public final int end;
    public final int line;
    public final boolean rtl;
    public final int clusterStart;
    public final int clusterCount;

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
