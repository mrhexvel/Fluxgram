package org.telegram.messenger.plugins.api;

public interface ComposerTextInsertionEffect extends ComposerEffect {
    void onTextInserted(ComposerTextInsertion insertion) throws Exception;
}
