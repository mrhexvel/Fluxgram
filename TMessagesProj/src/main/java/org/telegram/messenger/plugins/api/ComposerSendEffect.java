package org.telegram.messenger.plugins.api;

public interface ComposerSendEffect extends ComposerEffect {
    void onSend(ComposerSendEvent event) throws Exception;
}
