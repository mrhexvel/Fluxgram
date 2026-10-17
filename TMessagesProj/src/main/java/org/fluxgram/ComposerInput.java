package org.fluxgram;

public final class ComposerInput {
    public final int insertedCount;
    public final int removedCount;
    public final int textLength;
    public final boolean bulk;
    public final boolean composing;

    public ComposerInput(int insertedCount, int removedCount, int textLength, boolean bulk, boolean composing) {
        this.insertedCount = insertedCount;
        this.removedCount = removedCount;
        this.textLength = textLength;
        this.bulk = bulk;
        this.composing = composing;
    }
}
