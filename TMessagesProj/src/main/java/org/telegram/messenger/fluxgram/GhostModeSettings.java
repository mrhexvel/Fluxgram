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

    private GhostModeSettings() {
    }

    public static boolean isEnabled(int account) {
        return get(account, ENABLED);
    }

    public static void setEnabled(int account, boolean enabled) {
        set(account, ENABLED, enabled);
    }

    public static boolean hidesOnline(int account) {
        return get(account, HIDE_ONLINE);
    }

    public static void setHidesOnline(int account, boolean enabled) {
        set(account, HIDE_ONLINE, enabled);
    }

    public static boolean shouldHideOnline(int account) {
        return isEnabled(account) && hidesOnline(account);
    }

    public static boolean hidesTyping(int account) {
        return get(account, HIDE_TYPING);
    }

    public static void setHidesTyping(int account, boolean enabled) {
        set(account, HIDE_TYPING, enabled);
    }

    public static boolean hidesReadReceipts(int account) {
        return get(account, HIDE_READ_RECEIPTS);
    }

    public static void setHidesReadReceipts(int account, boolean enabled) {
        set(account, HIDE_READ_RECEIPTS, enabled);
    }

    public static boolean shouldHideReadReceipts(int account) {
        return isEnabled(account) && hidesReadReceipts(account);
    }

    public static boolean hidesStoryViews(int account) {
        return get(account, HIDE_STORY_VIEWS);
    }

    public static void setHidesStoryViews(int account, boolean enabled) {
        set(account, HIDE_STORY_VIEWS, enabled);
    }

    public static void clearAccount(int account) {
        preferences(account).edit().clear().apply();
        GhostModeReadState.clearAccount(account);
    }

    private static boolean get(int account, String key) {
        SharedPreferences preferences = activePreferences(account);

        return preferences != null && preferences.getBoolean(key, false);
    }

    private static void set(int account, String key, boolean value) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences != null) {
            preferences.edit().putBoolean(key, value).apply();
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
