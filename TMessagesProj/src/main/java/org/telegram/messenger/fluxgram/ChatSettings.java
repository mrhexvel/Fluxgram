package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class ChatSettings {
    private static final String HIDE_SEND_AS = "fluxgram_hide_send_as";
    private static final String HIDE_KEYBOARD_ON_SCROLL = "fluxgram_hide_keyboard_on_scroll";
    private static final String ADD_COMMA_AFTER_MENTION = "fluxgram_add_comma_after_mention";
    private static final String SHOW_MESSAGE_REACTIONS = "fluxgram_show_message_reactions";

    private static volatile boolean loaded;
    private static volatile boolean hideSendAs;
    private static volatile boolean hideKeyboardOnScroll;
    private static volatile boolean addCommaAfterMention;
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

    public static boolean hidesKeyboardOnScroll() {
        ensureLoaded();

        return hideKeyboardOnScroll;
    }

    public static synchronized void setHidesKeyboardOnScroll(boolean hidden) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(HIDE_KEYBOARD_ON_SCROLL, hidden)
                .apply();

        hideKeyboardOnScroll = hidden;
    }

    public static boolean addsCommaAfterMention() {
        ensureLoaded();

        return addCommaAfterMention;
    }

    public static synchronized void setAddsCommaAfterMention(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(ADD_COMMA_AFTER_MENTION, enabled)
                .apply();

        addCommaAfterMention = enabled;
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
                hideKeyboardOnScroll = preferences.getBoolean(HIDE_KEYBOARD_ON_SCROLL, false);
            } catch (ClassCastException exception) {
                hideKeyboardOnScroll = false;
            }
            try {
                addCommaAfterMention = preferences.getBoolean(ADD_COMMA_AFTER_MENTION, false);
            } catch (ClassCastException exception) {
                addCommaAfterMention = false;
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
