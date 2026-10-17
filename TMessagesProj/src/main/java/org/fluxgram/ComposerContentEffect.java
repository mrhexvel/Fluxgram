package org.fluxgram;

/**
 * Exclusive owner of bounded draw-only translation for the composer's native content.
 * Text, hint, selection, and cursor move together without changing layout or hit testing.
 */
public interface ComposerContentEffect extends ComposerEffect {
    /** Creates one content effect per eligible surface on the main thread. */
    interface Factory {
        /** Creates a lifecycle-owned content effect for {@code surface}. */
        ComposerContentEffect create(ComposerSurface surface) throws Exception;
    }

    /**
     * Returns the current main-thread presentation, or {@code null} for native drawing.
     * Invalid values suspend this registration and fall back to native drawing.
     */
    ComposerContentPresentation presentContent() throws Exception;
}
