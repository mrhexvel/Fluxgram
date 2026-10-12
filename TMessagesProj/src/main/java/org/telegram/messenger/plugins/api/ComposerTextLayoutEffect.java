package org.telegram.messenger.plugins.api;

public interface ComposerTextLayoutEffect extends ComposerTextEffect {
    void onTextLayoutChanged(ComposerTextLayoutChange change) throws Exception;
}
