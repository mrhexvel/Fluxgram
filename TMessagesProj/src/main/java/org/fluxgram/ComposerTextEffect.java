package org.fluxgram;

public interface ComposerTextEffect extends ComposerEffect {
    interface Factory {
        ComposerTextEffect create(ComposerSurface surface) throws Exception;
    }

    ComposerTextCandidate selectCandidate(ComposerTextChange change) throws Exception;

    ComposerTextPresentation present(ComposerTextCapture capture) throws Exception;
}
