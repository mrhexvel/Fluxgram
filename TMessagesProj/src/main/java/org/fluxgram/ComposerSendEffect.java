package org.fluxgram;

/** Receives successful starts of ordinary new text sends from this composer surface. */
public interface ComposerSendEffect extends ComposerEffect {
    /**
     * Runs on the main thread before the field is cleared.
     * The event is not a server acknowledgement or delivery notification.
     */
    void onSend(ComposerSendEvent event) throws Exception;
}
