package org.fluxgram;

public interface ComposerTextInsertionEffect extends ComposerEffect {
    void onTextInserted(ComposerTextInsertion insertion) throws Exception;
}
