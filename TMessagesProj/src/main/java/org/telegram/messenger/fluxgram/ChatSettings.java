package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class ChatSettings {
    private static final String HIDE_SEND_AS = "fluxgram_hide_send_as";

    private static volatile boolean loaded;
    private static volatile boolean hideSendAs;

    public static boolean hidesSendAs() {
        ensureLoaded();

        return hideSendAs;
    }

    public static synchronized void setHidesSendAs(boolean hidden) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(HIDE_SEND_AS, hidden)
                .apply();

        hideSendAs = hidden;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (ChatSettings.class) {
            if (loaded) {
                return;
            }

            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            try {
                hideSendAs = preferences.getBoolean(HIDE_SEND_AS, false);
            } catch (ClassCastException exception) {
                hideSendAs = false;
            }
            loaded = true;
        }
    }

    private ChatSettings() {
    }
}
