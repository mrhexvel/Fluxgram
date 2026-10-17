package org.fluxgram;

/**
 * Callback-scoped metadata for host-captured native text pixels.
 * The capture is host-owned, tied to an exact field revision/state, and must not be retained.
 */
public interface ComposerTextCapture {
    /** Returns the text revision captured by the host. */
    long getRevision();

    /** Returns the inclusive UTF-16 start offset. */
    int getStart();

    /** Returns the exclusive UTF-16 end offset. */
    int getEnd();

    /** Returns the capture's left edge in field-local physical pixels. */
    float getLeft();

    /** Returns the capture's top edge in field-local physical pixels. */
    float getTop();

    /** Returns the capture's right edge in field-local physical pixels. */
    float getRight();

    /** Returns the capture's bottom edge in field-local physical pixels. */
    float getBottom();

    /** Returns the captured text baseline in field-local physical pixels. */
    float getBaseline();
}
