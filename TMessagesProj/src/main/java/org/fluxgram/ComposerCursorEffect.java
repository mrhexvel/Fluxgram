package org.fluxgram;

public interface ComposerCursorEffect extends ComposerEffect {
    interface Factory {
        ComposerCursorEffect create(ComposerSurface surface) throws Exception;
    }

    ComposerCursorPresentation presentCursor(ComposerCursorSnapshot cursor) throws Exception;
}
