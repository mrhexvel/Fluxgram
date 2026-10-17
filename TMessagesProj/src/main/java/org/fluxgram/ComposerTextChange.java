package org.fluxgram;

/**
 * Immutable bounded text mutation delivered before host capture selection.
 * Text and every offset/count use Java UTF-16 code units; composing offsets may be negative.
 */
public final class ComposerTextChange {
    public final long revision;
    public final String beforeText;
    public final String text;
    public final int start;
    public final int removedCount;
    public final int insertedCount;
    public final int selectionStart;
    public final int selectionEnd;
    public final int composingStart;
    public final int composingEnd;

    /**
     * Creates a change tied to one monotonically increasing surface revision. The host provides
     * this object only when both before and after text fit the 4096 UTF-16-unit bound.
     */
    public ComposerTextChange(long revision, String beforeText, String text, int start,
            int removedCount, int insertedCount, int selectionStart, int selectionEnd,
            int composingStart, int composingEnd) {
        this.revision = revision;
        this.beforeText = beforeText;
        this.text = text;
        this.start = start;
        this.removedCount = removedCount;
        this.insertedCount = insertedCount;
        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
        this.composingStart = composingStart;
        this.composingEnd = composingEnd;
    }
}
