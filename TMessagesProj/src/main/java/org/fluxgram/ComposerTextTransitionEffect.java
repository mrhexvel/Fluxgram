package org.fluxgram;

/** A layout effect that may draw a detached bounded capture of reliably removed native text. */
public interface ComposerTextTransitionEffect extends ComposerTextLayoutEffect {
    /**
     * Returns a main-thread presentation for the callback-scoped removed capture, or {@code null}
     * to release it. Stale or unsupported captures are discarded without replacing current text.
     */
    ComposerTextPresentation presentRemovedText(
            ComposerTextLayoutChange change, ComposerTextCapture capture) throws Exception;
}
