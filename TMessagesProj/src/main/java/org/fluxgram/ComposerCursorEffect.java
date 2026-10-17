package org.fluxgram;

/** Exclusive draw-only presenter for the native composer cursor. */
public interface ComposerCursorEffect extends ComposerEffect {
    /** Creates one cursor effect per eligible surface on the main thread. */
    interface Factory {
        /** Creates a lifecycle-owned cursor effect for {@code surface}. */
        ComposerCursorEffect create(ComposerSurface surface) throws Exception;
    }

    /**
     * Returns the current main-thread cursor presentation, or {@code null} for native drawing.
     * Invalid values suspend the registration; host capture failures use native drawing.
     */
    ComposerCursorPresentation presentCursor(ComposerCursorSnapshot cursor) throws Exception;
}
