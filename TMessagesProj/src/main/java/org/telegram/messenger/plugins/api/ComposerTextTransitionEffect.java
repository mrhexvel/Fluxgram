package org.telegram.messenger.plugins.api;

public interface ComposerTextTransitionEffect extends ComposerTextLayoutEffect {
    ComposerTextPresentation presentRemovedText(
            ComposerTextLayoutChange change, ComposerTextCapture capture) throws Exception;
}
