package org.telegram.messenger.plugins.api;

public interface ComposerSelectionEffect extends ComposerEffect {
    interface Factory {
        ComposerSelectionEffect create(ComposerSurface surface) throws Exception;
    }

    ComposerSelectionPresentation presentSelection(ComposerSelectionSnapshot selection) throws Exception;
}
