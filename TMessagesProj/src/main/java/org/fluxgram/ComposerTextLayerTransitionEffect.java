package org.fluxgram;

public interface ComposerTextLayerTransitionEffect extends ComposerTextTransitionEffect {
    @Override
    default ComposerTextPresentation presentRemovedText(
            ComposerTextLayoutChange change, ComposerTextCapture capture) throws Exception {
        return null;
    }

    ComposerTextLayers presentRemovedTextLayers(
            ComposerTextLayoutChange change, ComposerTextCapture capture) throws Exception;
}
