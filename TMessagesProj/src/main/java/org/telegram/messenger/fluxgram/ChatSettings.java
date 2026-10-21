package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class ChatSettings {
    private static final String HIDE_SEND_AS = "fluxgram_hide_send_as";
    private static final String SHOW_MESSAGE_REACTIONS = "fluxgram_show_message_reactions";

    private static volatile boolean loaded;
    private static volatile boolean hideSendAs;
    private static volatile boolean showMessageReactions;

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

    public static boolean showsMessageReactions() {
        ensureLoaded();

        return showMessageReactions;
    }

    public static synchronized void setShowsMessageReactions(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_MESSAGE_REACTIONS, shown)
                .apply();

        showMessageReactions = shown;
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
            try {
                showMessageReactions = preferences.getBoolean(SHOW_MESSAGE_REACTIONS, true);
            } catch (ClassCastException exception) {
                showMessageReactions = true;
            }
            loaded = true;
        }
    }

    private ChatSettings() {
    }
}
