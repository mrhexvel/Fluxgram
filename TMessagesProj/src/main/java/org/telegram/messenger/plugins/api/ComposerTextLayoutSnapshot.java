package org.telegram.messenger.plugins.api;

public final class ComposerTextLayoutSnapshot {
    public final long revision;
    public final String text;
    public final int selectionStart;
    public final int selectionEnd;
    public final int composingStart;
    public final int composingEnd;
    public final int width;
    public final int height;
    public final float layoutOriginX;
    public final float layoutOriginY;
    public final int scrollX;
    public final int scrollY;
    public final boolean truncated;
    public final boolean unsupportedSpans;
    public final boolean unsupportedClusters;
    private final ComposerTextLineSnapshot[] lines;
    private final ComposerTextClusterSnapshot[] clusters;

    public ComposerTextLayoutSnapshot(long revision, String text,
            int selectionStart, int selectionEnd, int composingStart, int composingEnd,
            int width, int height, float layoutOriginX, float layoutOriginY, int scrollX, int scrollY,
            boolean truncated, boolean unsupportedSpans,
            ComposerTextLineSnapshot[] lines) {
        this(revision, text, selectionStart, selectionEnd, composingStart, composingEnd,
                width, height, layoutOriginX, layoutOriginY, scrollX, scrollY,
                truncated, unsupportedSpans, false, lines, null);
    }

    public ComposerTextLayoutSnapshot(long revision, String text,
            int selectionStart, int selectionEnd, int composingStart, int composingEnd,
            int width, int height, float layoutOriginX, float layoutOriginY, int scrollX, int scrollY,
            boolean truncated, boolean unsupportedSpans, boolean unsupportedClusters,
            ComposerTextLineSnapshot[] lines, ComposerTextClusterSnapshot[] clusters) {
        this.revision = revision;
        this.text = text;
        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
        this.composingStart = composingStart;
        this.composingEnd = composingEnd;
        this.width = width;
        this.height = height;
        this.layoutOriginX = layoutOriginX;
        this.layoutOriginY = layoutOriginY;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
        this.truncated = truncated;
        this.unsupportedSpans = unsupportedSpans;
        this.unsupportedClusters = unsupportedClusters;
        this.lines = lines == null ? new ComposerTextLineSnapshot[0] : lines.clone();
        this.clusters = clusters == null ? new ComposerTextClusterSnapshot[0] : clusters.clone();
    }

    public int getLineCount() {
        return lines.length;
    }

    public ComposerTextLineSnapshot getLine(int index) {
        return lines[index];
    }

    public int getClusterCount() {
        return clusters.length;
    }

    public ComposerTextClusterSnapshot getCluster(int index) {
        return clusters[index];
    }
}
