package org.fluxgram;

public interface ComposerContentEffect extends ComposerEffect {
    interface Factory {
        ComposerContentEffect create(ComposerSurface surface) throws Exception;
    }

    ComposerContentPresentation presentContent() throws Exception;
}
