package org.fluxgram;

/** Exclusive owner of bounded replacement presentation for selected native text pixels. */
public interface ComposerTextEffect extends ComposerEffect {
    /** Creates one text effect per eligible surface on the main thread. */
    interface Factory {
        /** Creates a lifecycle-owned text effect for {@code surface}. */
        ComposerTextEffect create(ComposerSurface surface) throws Exception;
    }

    /**
     * Selects on the main thread a non-empty UTF-16 range from the supplied revision, or
     * {@code null} for native text. The host revalidates revision, range, text, selection,
     * composing state, layout, and shaping.
     */
    ComposerTextCandidate selectCandidate(ComposerTextChange change) throws Exception;

    /**
     * Returns on the main thread a bounded presentation for the callback-scoped host capture, or
     * {@code null} to end it and restore native text. Unsupported or stale captures fall back to
     * one native draw.
     */
    ComposerTextPresentation present(ComposerTextCapture capture) throws Exception;
}
