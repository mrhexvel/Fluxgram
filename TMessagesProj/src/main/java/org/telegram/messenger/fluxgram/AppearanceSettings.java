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
    private static final String AVATAR_CORNER_RADIUS = "avatar_corner_radius";
    private static final String UNIFIED_AVATAR_RADIUS = "unified_avatar_radius";
    private static final String FAB_CORNER_RADIUS = "fab_corner_radius";
    private static final String SETTINGS_SECTION_RADIUS = "settings_section_radius";
    private static final String SETTINGS_DIVIDER_STYLE = "settings_divider_style";
    private static final String GLASS_OUTLINE_STYLE = "glass_outline_style";
    private static final String GOOEY_AVATAR = "gooey_avatar";
    private static final String CUSTOM_CHAT_THEMES = "custom_chat_themes";
    private static final String PROFILE_BACKGROUND_COLOR = "profile_background_color";
    private static final String PROFILE_BACKGROUND_EMOJI = "profile_background_emoji";

    public static final int TITLE_APP_NAME = 0;
    public static final int TITLE_USERNAME = 1;
    public static final int TITLE_NAME = 2;
    public static final int TITLE_CHATS = 3;
    public static final int MAX_FAB_CORNER_RADIUS = 24;
    public static final int DEFAULT_SETTINGS_SECTION_RADIUS = 20;
    public static final int MAX_SETTINGS_SECTION_RADIUS = 28;
    public static final int DIVIDER_HIDDEN = 0;
    public static final int DIVIDER_LINE = 1;
    public static final int DIVIDER_SEGMENTS = 2;
    public static final int GLASS_OUTLINE_GLARE = 0;
    public static final int GLASS_OUTLINE_SOLID = 1;
    public static final int GLASS_OUTLINE_HIDDEN = 2;

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

    public static int getAvatarCornerRadius(int account) {
        int radius = getInt(account, AVATAR_CORNER_RADIUS, AvatarGeometry.MAX_CORNER_RADIUS);
        return Math.max(0, Math.min(AvatarGeometry.MAX_CORNER_RADIUS, radius));
    }

    public static void setAvatarCornerRadius(int account, int radius) {
        if (radius < 0 || radius > AvatarGeometry.MAX_CORNER_RADIUS) {
            return;
        }

        setInt(account, AVATAR_CORNER_RADIUS, radius);
    }

    public static boolean usesUnifiedAvatarRadius(int account) {
        return getBoolean(account, UNIFIED_AVATAR_RADIUS);
    }

    public static void setUsesUnifiedAvatarRadius(int account, boolean unified) {
        setBoolean(account, UNIFIED_AVATAR_RADIUS, unified);
    }

    public static int getFabCornerRadius(int account) {
        int radius = getInt(account, FAB_CORNER_RADIUS, MAX_FAB_CORNER_RADIUS);
        return Math.max(0, Math.min(MAX_FAB_CORNER_RADIUS, radius));
    }

    public static void setFabCornerRadius(int account, int radius) {
        if (radius < 0 || radius > MAX_FAB_CORNER_RADIUS) {
            return;
        }

        setInt(account, FAB_CORNER_RADIUS, radius);
    }

    public static int getSettingsSectionRadius(int account) {
        int radius = getInt(account, SETTINGS_SECTION_RADIUS, DEFAULT_SETTINGS_SECTION_RADIUS);
        return Math.max(0, Math.min(MAX_SETTINGS_SECTION_RADIUS, radius));
    }

    public static void setSettingsSectionRadius(int account, int radius) {
        if (radius < 0 || radius > MAX_SETTINGS_SECTION_RADIUS) {
            return;
        }

        setInt(account, SETTINGS_SECTION_RADIUS, radius);
    }

    public static int getSettingsDividerStyle(int account) {
        int style = getInt(account, SETTINGS_DIVIDER_STYLE, DIVIDER_LINE);

        if (style < DIVIDER_HIDDEN || style > DIVIDER_SEGMENTS) {
            return DIVIDER_LINE;
        }

        return style;
    }

    public static void setSettingsDividerStyle(int account, int style) {
        if (style < DIVIDER_HIDDEN || style > DIVIDER_SEGMENTS) {
            return;
        }

        setInt(account, SETTINGS_DIVIDER_STYLE, style);
    }

    public static int getGlassOutlineStyle(int account) {
        int style = getInt(account, GLASS_OUTLINE_STYLE, GLASS_OUTLINE_GLARE);

        if (style < GLASS_OUTLINE_GLARE || style > GLASS_OUTLINE_HIDDEN) {
            return GLASS_OUTLINE_GLARE;
        }

        return style;
    }

    public static void setGlassOutlineStyle(int account, int style) {
        if (style < GLASS_OUTLINE_GLARE || style > GLASS_OUTLINE_HIDDEN) {
            return;
        }

        setInt(account, GLASS_OUTLINE_STYLE, style);
    }

    public static boolean usesGooeyAvatar(int account) {
        return getBoolean(account, GOOEY_AVATAR, true);
    }

    public static void setUsesGooeyAvatar(int account, boolean enabled) {
        setBoolean(account, GOOEY_AVATAR, enabled);
    }

    public static boolean usesCustomChatThemes(int account) {
        return getBoolean(account, CUSTOM_CHAT_THEMES, true);
    }

    public static void setUsesCustomChatThemes(int account, boolean enabled) {
        setBoolean(account, CUSTOM_CHAT_THEMES, enabled);
    }

    public static boolean showsProfileBackgroundColor(int account) {
        return getBoolean(account, PROFILE_BACKGROUND_COLOR, true);
    }

    public static void setShowsProfileBackgroundColor(int account, boolean shown) {
        setBoolean(account, PROFILE_BACKGROUND_COLOR, shown);
    }

    public static boolean showsProfileBackgroundEmoji(int account) {
        return getBoolean(account, PROFILE_BACKGROUND_EMOJI, true);
    }

    public static void setShowsProfileBackgroundEmoji(int account, boolean shown) {
        setBoolean(account, PROFILE_BACKGROUND_EMOJI, shown);
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
        return getBoolean(account, key, false);
    }

    private static boolean getBoolean(int account, String key, boolean defaultValue) {
        SharedPreferences preferences = activePreferences(account);

        return preferences == null ? defaultValue : preferences.getBoolean(key, defaultValue);
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
