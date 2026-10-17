package org.fluxgram;

public final class ComposerTextLayoutSnapshot {
    public static final int SPAN_UNDERLINE = 1;
    public static final int SPAN_SUGGESTION = 1 << 1;
    public static final int SPAN_METRIC = 1 << 2;
    public static final int SPAN_REPLACEMENT = 1 << 3;
    public static final int SPAN_PARAGRAPH = 1 << 4;
    public static final int SPAN_UNKNOWN_CHARACTER = 1 << 5;

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
    public final int spanFlags;
    public final ComposerTextFontSnapshot font;
    private final ComposerTextLineSnapshot[] lines;
    private final ComposerTextClusterSnapshot[] clusters;
    private final ComposerTextRunSnapshot[] runs;

    public ComposerTextLayoutSnapshot(long revision, String text,
            int selectionStart, int selectionEnd, int composingStart, int composingEnd,
            int width, int height, float layoutOriginX, float layoutOriginY, int scrollX, int scrollY,
            boolean truncated, boolean unsupportedSpans,
            ComposerTextLineSnapshot[] lines) {
        this(revision, text, selectionStart, selectionEnd, composingStart, composingEnd,
                width, height, layoutOriginX, layoutOriginY, scrollX, scrollY,
                truncated, unsupportedSpans, false, 0, null, lines, null, null);
    }

    public ComposerTextLayoutSnapshot(long revision, String text,
            int selectionStart, int selectionEnd, int composingStart, int composingEnd,
            int width, int height, float layoutOriginX, float layoutOriginY, int scrollX, int scrollY,
            boolean truncated, boolean unsupportedSpans, boolean unsupportedClusters,
            ComposerTextLineSnapshot[] lines, ComposerTextClusterSnapshot[] clusters) {
        this(revision, text, selectionStart, selectionEnd, composingStart, composingEnd,
                width, height, layoutOriginX, layoutOriginY, scrollX, scrollY,
                truncated, unsupportedSpans, unsupportedClusters, 0, null, lines, clusters, null);
    }

    public ComposerTextLayoutSnapshot(long revision, String text,
            int selectionStart, int selectionEnd, int composingStart, int composingEnd,
            int width, int height, float layoutOriginX, float layoutOriginY, int scrollX, int scrollY,
            boolean truncated, boolean unsupportedSpans, boolean unsupportedClusters,
            int spanFlags, ComposerTextFontSnapshot font,
            ComposerTextLineSnapshot[] lines, ComposerTextClusterSnapshot[] clusters,
            ComposerTextRunSnapshot[] runs) {
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
        this.spanFlags = spanFlags;
        this.font = font;
        this.lines = lines == null ? new ComposerTextLineSnapshot[0] : lines.clone();
        this.clusters = clusters == null ? new ComposerTextClusterSnapshot[0] : clusters.clone();
        this.runs = runs == null ? new ComposerTextRunSnapshot[0] : runs.clone();
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

    public int getRunCount() {
        return runs.length;
    }

    public ComposerTextRunSnapshot getRun(int index) {
        return runs[index];
    }
}
