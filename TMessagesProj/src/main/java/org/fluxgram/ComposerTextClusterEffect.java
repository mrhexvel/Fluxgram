package org.fluxgram;

public interface ComposerTextClusterEffect extends ComposerTextLayoutEffect {
    @Override
    default ComposerTextCandidate selectCandidate(ComposerTextChange change) throws Exception {
        return null;
    }

    @Override
    default ComposerTextPresentation present(ComposerTextCapture capture) throws Exception {
        return null;
    }

    @Override
    default void onTextLayoutChanged(ComposerTextLayoutChange change) throws Exception {
    }

    ComposerTextClusterSelection selectClusters(ComposerTextLayoutChange change) throws Exception;

    ComposerTextClusterPresentation presentClusters(ComposerTextClusterCapture capture) throws Exception;
}
