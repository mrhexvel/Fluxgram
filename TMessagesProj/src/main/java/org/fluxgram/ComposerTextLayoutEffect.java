package org.fluxgram;

public interface ComposerTextLayoutEffect extends ComposerTextEffect {
    void onTextLayoutChanged(ComposerTextLayoutChange change) throws Exception;
}
