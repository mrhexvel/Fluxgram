package org.fluxgram;

/** A removed-text transition that may replay the same host capture in up to two bounded layers. */
public interface ComposerTextLayerTransitionEffect extends ComposerTextTransitionEffect {
    /** {@inheritDoc} */
    @Override
    default ComposerTextPresentation presentRemovedText(
            ComposerTextLayoutChange change, ComposerTextCapture capture) throws Exception {
        return null;
    }

    /**
     * Returns one or two presentations for the callback-scoped removed capture, or {@code null}
     * to release it. Layer order is draw order.
     */
    ComposerTextLayers presentRemovedTextLayers(
            ComposerTextLayoutChange change, ComposerTextCapture capture) throws Exception;
}
