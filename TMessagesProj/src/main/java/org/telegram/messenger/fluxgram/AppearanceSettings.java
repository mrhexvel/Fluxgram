package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

public final class AppearanceSettings {
    private static final String OWNER = "owner";
    private static final String TITLE_MODE = "title_mode";
    private static final String HIDE_ACTION_BAR_STATUS = "hide_action_bar_status";
    private static final String CENTER_ACTION_BAR_TITLE = "center_action_bar_title";
    private static final String HIDE_STORIES = "hide_stories";
    private static final String HIDE_FLOATING_BUTTON = "hide_floating_button";
    private static final String HIDE_DIALOGS_SEARCH_BAR = "hide_dialogs_search_bar";
    private static final String SENDER_MINI_AVATARS = "sender_mini_avatars";
    private static final String FORCE_SNOW = "force_snow";

    public static final int TITLE_APP_NAME = 0;
    public static final int TITLE_USERNAME = 1;
    public static final int TITLE_NAME = 2;
    public static final int TITLE_CHATS = 3;

    private AppearanceSettings() {
    }

    public static int getTitleMode(int account) {
        int mode = getInt(account, TITLE_MODE, TITLE_APP_NAME);

        if (mode < TITLE_APP_NAME || mode > TITLE_CHATS) {
            return TITLE_APP_NAME;
        }

        return mode;
    }

    public static void setTitleMode(int account, int mode) {
        if (mode < TITLE_APP_NAME || mode > TITLE_CHATS) {
            return;
        }

        setInt(account, TITLE_MODE, mode);
    }

    public static boolean hidesActionBarStatus(int account) {
        return getBoolean(account, HIDE_ACTION_BAR_STATUS);
    }

    public static void setHidesActionBarStatus(int account, boolean hidden) {
        setBoolean(account, HIDE_ACTION_BAR_STATUS, hidden);
    }

    public static boolean centersActionBarTitle(int account) {
        return getBoolean(account, CENTER_ACTION_BAR_TITLE);
    }

    public static void setCentersActionBarTitle(int account, boolean centered) {
        setBoolean(account, CENTER_ACTION_BAR_TITLE, centered);
    }

    public static boolean hidesStories(int account) {
        return getBoolean(account, HIDE_STORIES);
    }

    public static void setHidesStories(int account, boolean hidden) {
        setBoolean(account, HIDE_STORIES, hidden);
    }

    public static boolean hidesFloatingButton(int account) {
        return getBoolean(account, HIDE_FLOATING_BUTTON);
    }

    public static void setHidesFloatingButton(int account, boolean hidden) {
        setBoolean(account, HIDE_FLOATING_BUTTON, hidden);
    }

    public static boolean hidesDialogsSearchBar(int account) {
        return getBoolean(account, HIDE_DIALOGS_SEARCH_BAR);
    }

    public static void setHidesDialogsSearchBar(int account, boolean hidden) {
        setBoolean(account, HIDE_DIALOGS_SEARCH_BAR, hidden);
    }

    public static boolean showsSenderMiniAvatars(int account) {
        return getBoolean(account, SENDER_MINI_AVATARS);
    }

    public static void setShowsSenderMiniAvatars(int account, boolean shown) {
        setBoolean(account, SENDER_MINI_AVATARS, shown);
    }

    public static boolean forcesSnow(int account) {
        return getBoolean(account, FORCE_SNOW);
    }

    public static void setForcesSnow(int account, boolean forced) {
        setBoolean(account, FORCE_SNOW, forced);
    }

    public static CharSequence getActionBarTitle(int account) {
        int mode = getTitleMode(account);

        if (mode == TITLE_CHATS) {
            return LocaleController.getString(R.string.FilterChats);
        }
        if (mode == TITLE_APP_NAME) {
            return LocaleController.getString(R.string.FluxGramAppName);
        }

        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (mode == TITLE_USERNAME) {
            String username = UserObject.getPublicUsername(user);

            if (!TextUtils.isEmpty(username)) {
                return username;
            }
        }

        return UserObject.getFirstName(user);
    }

    private static boolean getBoolean(int account, String key) {
        SharedPreferences preferences = activePreferences(account);

        return preferences != null && preferences.getBoolean(key, false);
    }

    private static void setBoolean(int account, String key, boolean value) {
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
                "fluxgram_appearance_" + account, Context.MODE_PRIVATE
        );
    }
}
