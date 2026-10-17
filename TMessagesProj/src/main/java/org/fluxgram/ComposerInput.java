package org.fluxgram;

/**
 * Immutable counts-only summary of coalesced composer input.
 * Counts and {@link #textLength} use UTF-16 code units; counts saturate at 65.
 */
public final class ComposerInput {
    public final int insertedCount;
    public final int removedCount;
    public final int textLength;
    public final boolean bulk;
    public final boolean composing;

    /**
     * Creates an input summary. {@code bulk} indicates more than 64 changed UTF-16 units or an
     * input whose full text exceeded the host's bounded inspection limit.
     */
    public ComposerInput(int insertedCount, int removedCount, int textLength, boolean bulk, boolean composing) {
        this.insertedCount = insertedCount;
        this.removedCount = removedCount;
        this.textLength = textLength;
        this.bulk = bulk;
        this.composing = composing;
    }
}
