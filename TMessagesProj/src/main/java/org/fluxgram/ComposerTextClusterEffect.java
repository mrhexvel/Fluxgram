package org.fluxgram;

/** Exclusive native-text effect that selects and presents exact Android shaping clusters. */
public interface ComposerTextClusterEffect extends ComposerTextLayoutEffect {
    /** {@inheritDoc} */
    @Override
    default ComposerTextCandidate selectCandidate(ComposerTextChange change) throws Exception {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    default ComposerTextPresentation present(ComposerTextCapture capture) throws Exception {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    default void onTextLayoutChanged(ComposerTextLayoutChange change) throws Exception {
    }

    /**
     * Selects on the main thread up to 64 ordered, non-overlapping exact clusters from
     * {@code change.after}, or {@code null} for native text. Revision and cluster boundaries are
     * revalidated by the host.
     */
    ComposerTextClusterSelection selectClusters(ComposerTextLayoutChange change) throws Exception;

    /**
     * Returns on the main thread one presentation entry per callback-scoped capture, tied to the
     * same revision, or {@code null} to release the batch and restore native drawing.
     */
    ComposerTextClusterPresentation presentClusters(ComposerTextClusterCapture capture) throws Exception;
}
