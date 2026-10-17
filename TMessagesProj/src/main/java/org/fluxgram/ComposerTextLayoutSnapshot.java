package org.fluxgram;

/**
 * Immutable bounded Android layout snapshot in field-local physical pixels.
 * Text and offsets use UTF-16 units. At most 16 visible lines and 256 shaping clusters are exposed;
 * truncation and unsupported spans/clusters are reported instead of approximated.
 */
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

    /** Creates a legacy line-only snapshot; arrays are defensively copied. */
    public ComposerTextLayoutSnapshot(long revision, String text,
            int selectionStart, int selectionEnd, int composingStart, int composingEnd,
            int width, int height, float layoutOriginX, float layoutOriginY, int scrollX, int scrollY,
            boolean truncated, boolean unsupportedSpans,
            ComposerTextLineSnapshot[] lines) {
        this(revision, text, selectionStart, selectionEnd, composingStart, composingEnd,
                width, height, layoutOriginX, layoutOriginY, scrollX, scrollY,
                truncated, unsupportedSpans, false, 0, null, lines, null, null);
    }

    /** Creates a snapshot with line and shaping-cluster geometry; arrays are defensively copied. */
    public ComposerTextLayoutSnapshot(long revision, String text,
            int selectionStart, int selectionEnd, int composingStart, int composingEnd,
            int width, int height, float layoutOriginX, float layoutOriginY, int scrollX, int scrollY,
            boolean truncated, boolean unsupportedSpans, boolean unsupportedClusters,
            ComposerTextLineSnapshot[] lines, ComposerTextClusterSnapshot[] clusters) {
        this(revision, text, selectionStart, selectionEnd, composingStart, composingEnd,
                width, height, layoutOriginX, layoutOriginY, scrollX, scrollY,
                truncated, unsupportedSpans, unsupportedClusters, 0, null, lines, clusters, null);
    }

    /**
     * Creates a complete snapshot with directional runs and neutral font metadata. Width, height,
     * origins, and scroll values are physical pixels; arrays are defensively copied.
     */
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

    /** Returns the number of captured visible lines, at most 16. */
    public int getLineCount() {
        return lines.length;
    }

    /** Returns a captured visible line by snapshot-local index. */
    public ComposerTextLineSnapshot getLine(int index) {
        return lines[index];
    }

    /** Returns the number of exact shaping clusters, at most 256. */
    public int getClusterCount() {
        return clusters.length;
    }

    /** Returns an exact shaping cluster by snapshot-global index. */
    public ComposerTextClusterSnapshot getCluster(int index) {
        return clusters[index];
    }

    /** Returns the number of captured directional text runs. */
    public int getRunCount() {
        return runs.length;
    }

    /** Returns a directional run whose cluster range indexes this snapshot's cluster array. */
    public ComposerTextRunSnapshot getRun(int index) {
        return runs[index];
    }
}
