package org.fluxgram;

/** A text effect that also observes bounded before/after Android layout snapshots. */
public interface ComposerTextLayoutEffect extends ComposerTextEffect {
    /**
     * Receives the final coalesced layout change on the main thread after layout is available.
     * Snapshots are immutable; unsupported geometry is reported explicitly rather than estimated.
     */
    void onTextLayoutChanged(ComposerTextLayoutChange change) throws Exception;
}
