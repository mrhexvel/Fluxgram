package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class ChatSettings {
    private static final String HIDE_SEND_AS = "fluxgram_hide_send_as";
    private static final String HIDE_KEYBOARD_ON_SCROLL = "fluxgram_hide_keyboard_on_scroll";
    private static final String ADD_COMMA_AFTER_MENTION = "fluxgram_add_comma_after_mention";
    private static final String QUICK_CHANNEL_TOPIC_TRANSITIONS = "fluxgram_quick_channel_topic_transitions";
    private static final String SHOW_MESSAGE_REACTIONS = "fluxgram_show_message_reactions";
    private static final String SHOW_EDITED_MARK = "fluxgram_show_edited_mark";
    private static final String SHOW_MESSAGE_TAILS = "fluxgram_show_message_tails";

    private static volatile boolean loaded;
    private static volatile boolean hideSendAs;
    private static volatile boolean hideKeyboardOnScroll;
    private static volatile boolean addCommaAfterMention;
    private static volatile boolean quickChannelTopicTransitions;
    private static volatile boolean showMessageReactions;
    private static volatile boolean showEditedMark;
    private static volatile boolean showMessageTails;

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

    public static boolean allowsQuickChannelTopicTransitions() {
        ensureLoaded();

        return quickChannelTopicTransitions;
    }

    public static synchronized void setAllowsQuickChannelTopicTransitions(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(QUICK_CHANNEL_TOPIC_TRANSITIONS, enabled)
                .apply();

        quickChannelTopicTransitions = enabled;
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

    public static boolean showsEditedMark() {
        ensureLoaded();

        return showEditedMark;
    }

    public static synchronized void setShowsEditedMark(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_EDITED_MARK, shown)
                .apply();

        showEditedMark = shown;
    }

    public static boolean showsMessageTails() {
        ensureLoaded();

        return showMessageTails;
    }

    public static synchronized void setShowsMessageTails(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_MESSAGE_TAILS, shown)
                .apply();

        showMessageTails = shown;
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
                quickChannelTopicTransitions = preferences.getBoolean(QUICK_CHANNEL_TOPIC_TRANSITIONS, false);
            } catch (ClassCastException exception) {
                quickChannelTopicTransitions = false;
            }
            try {
                showMessageReactions = preferences.getBoolean(SHOW_MESSAGE_REACTIONS, true);
            } catch (ClassCastException exception) {
                showMessageReactions = true;
            }
            try {
                showEditedMark = preferences.getBoolean(SHOW_EDITED_MARK, true);
            } catch (ClassCastException exception) {
                showEditedMark = true;
            }
            try {
                showMessageTails = preferences.getBoolean(SHOW_MESSAGE_TAILS, true);
            } catch (ClassCastException exception) {
                showMessageTails = true;
            }
            loaded = true;
        }
    }

    private ChatSettings() {
    }
}
