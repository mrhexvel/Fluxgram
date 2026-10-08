package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public final class GhostModeSettings {
    private static final String OWNER = "owner";
    private static final String ENABLED = "enabled";
    private static final String HIDE_ONLINE = "hide_online";
    private static final String HIDE_TYPING = "hide_typing";
    private static final String HIDE_READ_RECEIPTS = "hide_read_receipts";
    private static final String HIDE_STORY_VIEWS = "hide_story_views";
    private static final String AUTOMATIC_OFFLINE = "automatic_offline";
    private static final String READ_ON_ACTIONS = "read_on_actions";
    private static final String USE_SEND_DELAY = "use_send_delay";
    private static final String SILENT_SEND_MODE = "silent_send_mode";
    private static final String SUGGEST_FOR_STORIES = "suggest_for_stories";

    public static final int SILENT_SEND_NEVER = 0;
    public static final int SILENT_SEND_IN_GHOST_MODE = 1;
    public static final int SILENT_SEND_ALWAYS = 2;

    private GhostModeSettings() {
    }

    public static boolean isEnabled(int account) {
        return GhostModeTemporarySettings.isActive(account) || get(account, ENABLED);
    }

    public static void setEnabled(int account, boolean enabled) {
        set(account, ENABLED, enabled);
    }

    public static boolean hidesOnline(int account) {
        return GhostModeTemporarySettings.isActive(account) || get(account, HIDE_ONLINE);
    }

    public static void setHidesOnline(int account, boolean enabled) {
        set(account, HIDE_ONLINE, enabled);
    }

    public static boolean shouldHideOnline(int account) {
        return isEnabled(account) && hidesOnline(account);
    }

    public static boolean hidesTyping(int account) {
        return GhostModeTemporarySettings.isActive(account) || get(account, HIDE_TYPING);
    }

    public static void setHidesTyping(int account, boolean enabled) {
        set(account, HIDE_TYPING, enabled);
    }

    public static boolean shouldHideTypingAction(int account, int action) {
        if (!isEnabled(account) || !hidesTyping(account)) {
            return false;
        }

        return action == 0 || action == 1 || action == 3 || action == 4 || action == 5 || action == 7 || action == 8 || action == 9 || action == 10;
    }

    public static boolean hidesReadReceipts(int account) {
        return GhostModeTemporarySettings.isActive(account) || get(account, HIDE_READ_RECEIPTS);
    }

    public static void setHidesReadReceipts(int account, boolean enabled) {
        set(account, HIDE_READ_RECEIPTS, enabled);
    }

    public static boolean shouldHideReadReceipts(int account) {
        return isEnabled(account) && hidesReadReceipts(account);
    }

    public static boolean hidesStoryViews(int account) {
        return GhostModeTemporarySettings.isActive(account) || get(account, HIDE_STORY_VIEWS);
    }

    public static void setHidesStoryViews(int account, boolean enabled) {
        set(account, HIDE_STORY_VIEWS, enabled);
    }

    public static boolean shouldHideStoryViews(int account) {
        return isEnabled(account) && hidesStoryViews(account);
    }

    public static boolean usesAutomaticOffline(int account) {
        return GhostModeTemporarySettings.isActive(account) || get(account, AUTOMATIC_OFFLINE);
    }

    public static void setUsesAutomaticOffline(int account, boolean enabled) {
        set(account, AUTOMATIC_OFFLINE, enabled);
    }

    public static boolean shouldUseAutomaticOffline(int account) {
        return isEnabled(account) && usesAutomaticOffline(account);
    }

    public static boolean readsOnActions(int account) {
        return get(account, READ_ON_ACTIONS, true);
    }

    public static void setReadsOnActions(int account, boolean enabled) {
        set(account, READ_ON_ACTIONS, enabled);
    }

    public static boolean shouldReadOnActions(int account) {
        return shouldHideReadReceipts(account) && readsOnActions(account);
    }

    public static boolean usesSendDelay(int account) {
        return get(account, USE_SEND_DELAY);
    }

    public static void setUsesSendDelay(int account, boolean enabled) {
        set(account, USE_SEND_DELAY, enabled);
    }

    public static boolean shouldUseSendDelay(int account) {
        return isEnabled(account) && usesSendDelay(account);
    }

    public static int getSilentSendMode(int account) {
        return getInt(account, SILENT_SEND_MODE, SILENT_SEND_NEVER);
    }

    public static void setSilentSendMode(int account, int mode) {
        if (mode < SILENT_SEND_NEVER || mode > SILENT_SEND_ALWAYS) {
            return;
        }

        setInt(account, SILENT_SEND_MODE, mode);
    }

    public static boolean shouldSendSilently(int account) {
        int mode = getSilentSendMode(account);

        if (mode == SILENT_SEND_ALWAYS) {
            return true;
        }
        if (mode == SILENT_SEND_IN_GHOST_MODE) {
            return isEnabled(account);
        }

        return false;
    }

    public static boolean suggestsForStories(int account) {
        return get(account, SUGGEST_FOR_STORIES, true);
    }

    public static void setSuggestsForStories(int account, boolean enabled) {
        set(account, SUGGEST_FOR_STORIES, enabled);
    }

    public static void clearAccount(int account) {
        preferences(account).edit().clear().apply();
        GhostModeTemporarySettings.clearAccount(account);
        GhostModeReadState.clearAccount(account);
        GhostModeStoryState.clearAccount(account);
        GhostModeDialogScope.clearAccount(account);
    }

    private static boolean get(int account, String key) {
        return get(account, key, false);
    }

    private static boolean get(int account, String key, boolean defaultValue) {
        SharedPreferences preferences = activePreferences(account);

        return preferences != null && preferences.getBoolean(key, defaultValue);
    }

    private static void set(int account, String key, boolean value) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences != null) {
            preferences.edit().putBoolean(key, value).apply();
        }
    }

    private static int getInt(int account, String key, int defaultValue) {
        SharedPreferences preferences = activePreferences(account);

        return preferences == null ? defaultValue : preferences.getInt(key, defaultValue);
    }

    private static void setInt(int account, String key, int value) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences != null) {
            preferences.edit().putInt(key, value).apply();
        }
    }

    private static synchronized SharedPreferences activePreferences(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();

        if (owner == 0) {
            return null;
        }

        SharedPreferences preferences = preferences(account);

        if (preferences.getLong(OWNER, 0) != owner) {
            preferences.edit().clear().putLong(OWNER, owner).apply();
        }

        return preferences;
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_ghost_mode_" + account, Context.MODE_PRIVATE
        );
    }

}
