package org.fluxgram;

public interface ComposerSendEffect extends ComposerEffect {
    void onSend(ComposerSendEvent event) throws Exception;
}
