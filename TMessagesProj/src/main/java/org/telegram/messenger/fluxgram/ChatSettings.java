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
    private static final String SHOW_MESSAGE_AVATAR_ONLINE = "fluxgram_show_message_avatar_online";
    private static final String SHOW_MESSAGE_VIEWS = "fluxgram_show_message_views";
    private static final String SHOW_MESSAGE_SHARE_BUTTON = "fluxgram_show_message_share_button";
    private static final String SHOW_MESSAGES_FROM_AUTHOR = "fluxgram_show_messages_from_author";
    private static final String GROUP_MESSAGE_MENU_ACTIONS = "fluxgram_group_message_menu_actions";
    private static final String SHOW_RECENT_ACTIONS_SHORTCUT = "fluxgram_show_recent_actions_shortcut";
    private static final String SHOW_ADMINISTRATORS_SHORTCUT = "fluxgram_show_administrators_shortcut";
    private static final String SHOW_PARTICIPANTS_SHORTCUT = "fluxgram_show_participants_shortcut";
    private static final String SHOW_PERMISSIONS_SHORTCUT = "fluxgram_show_permissions_shortcut";
    private static final String WIDE_CHANNEL_POSTS = "fluxgram_wide_channel_posts";
    private static final String SHOW_ATTACH_CAMERA_TILE = "fluxgram_show_attach_camera_tile";

    private static volatile boolean loaded;
    private static volatile boolean hideSendAs;
    private static volatile boolean hideKeyboardOnScroll;
    private static volatile boolean addCommaAfterMention;
    private static volatile boolean quickChannelTopicTransitions;
    private static volatile boolean showMessageReactions;
    private static volatile boolean showEditedMark;
    private static volatile boolean showMessageTails;
    private static volatile boolean showMessageAvatarOnline;
    private static volatile boolean showMessageViews;
    private static volatile boolean showMessageShareButton;
    private static volatile boolean showMessagesFromAuthor;
    private static volatile boolean groupMessageMenuActions;
    private static volatile boolean showRecentActionsShortcut;
    private static volatile boolean showAdministratorsShortcut;
    private static volatile boolean showParticipantsShortcut;
    private static volatile boolean showPermissionsShortcut;
    private static volatile boolean wideChannelPosts;
    private static volatile boolean showAttachCameraTile;

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

    public static boolean showsMessageAvatarOnline() {
        ensureLoaded();

        return showMessageAvatarOnline;
    }

    public static synchronized void setShowsMessageAvatarOnline(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_MESSAGE_AVATAR_ONLINE, shown)
                .apply();

        showMessageAvatarOnline = shown;
    }

    public static boolean showsMessageViews() {
        ensureLoaded();

        return showMessageViews;
    }

    public static synchronized void setShowsMessageViews(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_MESSAGE_VIEWS, shown)
                .apply();

        showMessageViews = shown;
    }

    public static boolean showsMessageShareButton() {
        ensureLoaded();

        return showMessageShareButton;
    }

    public static synchronized void setShowsMessageShareButton(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_MESSAGE_SHARE_BUTTON, shown)
                .apply();

        showMessageShareButton = shown;
    }

    public static boolean showsMessagesFromAuthor() {
        ensureLoaded();

        return showMessagesFromAuthor;
    }

    public static synchronized void setShowsMessagesFromAuthor(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_MESSAGES_FROM_AUTHOR, shown)
                .apply();

        showMessagesFromAuthor = shown;
    }

    public static boolean groupsMessageMenuActions() {
        ensureLoaded();

        return groupMessageMenuActions;
    }

    public static synchronized void setGroupsMessageMenuActions(boolean grouped) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(GROUP_MESSAGE_MENU_ACTIONS, grouped)
                .apply();

        groupMessageMenuActions = grouped;
    }

    public static boolean showsRecentActionsShortcut() {
        ensureLoaded();

        return showRecentActionsShortcut;
    }

    public static synchronized void setShowsRecentActionsShortcut(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_RECENT_ACTIONS_SHORTCUT, shown)
                .apply();

        showRecentActionsShortcut = shown;
    }

    public static boolean showsAdministratorsShortcut() {
        ensureLoaded();

        return showAdministratorsShortcut;
    }

    public static synchronized void setShowsAdministratorsShortcut(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_ADMINISTRATORS_SHORTCUT, shown)
                .apply();

        showAdministratorsShortcut = shown;
    }

    public static boolean showsParticipantsShortcut() {
        ensureLoaded();

        return showParticipantsShortcut;
    }

    public static synchronized void setShowsParticipantsShortcut(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_PARTICIPANTS_SHORTCUT, shown)
                .apply();

        showParticipantsShortcut = shown;
    }

    public static boolean showsPermissionsShortcut() {
        ensureLoaded();

        return showPermissionsShortcut;
    }

    public static synchronized void setShowsPermissionsShortcut(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_PERMISSIONS_SHORTCUT, shown)
                .apply();

        showPermissionsShortcut = shown;
    }

    public static boolean usesWideChannelPosts() {
        ensureLoaded();

        return wideChannelPosts;
    }

    public static synchronized void setUsesWideChannelPosts(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(WIDE_CHANNEL_POSTS, enabled)
                .apply();

        wideChannelPosts = enabled;
    }

    public static boolean showsAttachCameraTile() {
        ensureLoaded();

        return showAttachCameraTile;
    }

    public static synchronized void setShowsAttachCameraTile(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_ATTACH_CAMERA_TILE, shown)
                .apply();

        showAttachCameraTile = shown;
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
            try {
                showMessageAvatarOnline = preferences.getBoolean(SHOW_MESSAGE_AVATAR_ONLINE, false);
            } catch (ClassCastException exception) {
                showMessageAvatarOnline = false;
            }
            try {
                showMessageViews = preferences.getBoolean(SHOW_MESSAGE_VIEWS, true);
            } catch (ClassCastException exception) {
                showMessageViews = true;
            }
            try {
                showMessageShareButton = preferences.getBoolean(SHOW_MESSAGE_SHARE_BUTTON, true);
            } catch (ClassCastException exception) {
                showMessageShareButton = true;
            }
            try {
                showMessagesFromAuthor = preferences.getBoolean(SHOW_MESSAGES_FROM_AUTHOR, false);
            } catch (ClassCastException exception) {
                showMessagesFromAuthor = false;
            }
            try {
                groupMessageMenuActions = preferences.getBoolean(GROUP_MESSAGE_MENU_ACTIONS, true);
            } catch (ClassCastException exception) {
                groupMessageMenuActions = true;
            }
            try {
                showRecentActionsShortcut = preferences.getBoolean(SHOW_RECENT_ACTIONS_SHORTCUT, false);
            } catch (ClassCastException exception) {
                showRecentActionsShortcut = false;
            }
            try {
                showAdministratorsShortcut = preferences.getBoolean(SHOW_ADMINISTRATORS_SHORTCUT, false);
            } catch (ClassCastException exception) {
                showAdministratorsShortcut = false;
            }
            try {
                showParticipantsShortcut = preferences.getBoolean(SHOW_PARTICIPANTS_SHORTCUT, false);
            } catch (ClassCastException exception) {
                showParticipantsShortcut = false;
            }
            try {
                showPermissionsShortcut = preferences.getBoolean(SHOW_PERMISSIONS_SHORTCUT, false);
            } catch (ClassCastException exception) {
                showPermissionsShortcut = false;
            }
            try {
                wideChannelPosts = preferences.getBoolean(WIDE_CHANNEL_POSTS, false);
            } catch (ClassCastException exception) {
                wideChannelPosts = false;
            }
            try {
                showAttachCameraTile = preferences.getBoolean(SHOW_ATTACH_CAMERA_TILE, true);
            } catch (ClassCastException exception) {
                showAttachCameraTile = true;
            }
            loaded = true;
        }
    }

    private ChatSettings() {
    }
}
