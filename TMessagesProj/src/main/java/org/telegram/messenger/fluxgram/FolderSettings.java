package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;

public final class FolderSettings {
    private static final String OWNER = "owner";
    private static final String PRESENTATION_MODE = "presentation_mode";
    private static final String COUNTER_MODE = "counter_mode";
    private static final String SHOW_ALL_CHATS = "show_all_chats";

    public static final int MODE_TITLE_AND_ICON = 0;
    public static final int MODE_TITLE = 1;
    public static final int MODE_ICON = 2;

    public static final int COUNTER_ALL = 0;
    public static final int COUNTER_UNMUTED = 1;
    public static final int COUNTER_HIDDEN = 2;

    public static final class Policy {
        private final int presentationMode;
        private final int counterMode;
        private final boolean showAllChats;

        private Policy(int presentationMode, int counterMode, boolean showAllChats) {
            this.presentationMode = presentationMode;
            this.counterMode = counterMode;
            this.showAllChats = showAllChats;
        }

        public int getPresentationMode() {
            return presentationMode;
        }

        public int getCounterMode() {
            return counterMode;
        }

        public boolean showsAllChats() {
            return showAllChats;
        }
    }

    public static Policy getPolicy(int account) {
        SharedPreferences preferences = activePreferences(account);
        int mode = preferences == null
                ? MODE_TITLE_AND_ICON
                : preferences.getInt(PRESENTATION_MODE, MODE_TITLE_AND_ICON);
        int counterMode = preferences == null
                ? COUNTER_ALL
                : preferences.getInt(COUNTER_MODE, COUNTER_ALL);
        boolean showAllChats = preferences == null
                || preferences.getBoolean(SHOW_ALL_CHATS, true);

        return new Policy(
                validatePresentationMode(mode),
                validateCounterMode(counterMode),
                showAllChats
        );
    }

    public static void setPresentationMode(int account, int mode) {
        SharedPreferences preferences = activePreferences(account);
        if (preferences == null || mode != validatePresentationMode(mode)) {
            return;
        }

        preferences.edit().putInt(PRESENTATION_MODE, mode).apply();
    }

    public static void setCounterMode(int account, int mode) {
        SharedPreferences preferences = activePreferences(account);
        if (preferences == null || mode != validateCounterMode(mode)) {
            return;
        }

        preferences.edit().putInt(COUNTER_MODE, mode).apply();
    }

    public static void setShowsAllChats(int account, boolean show) {
        SharedPreferences preferences = activePreferences(account);
        if (preferences != null) {
            preferences.edit().putBoolean(SHOW_ALL_CHATS, show).apply();
        }
    }

    public static boolean isAllChatsVisible(
            Policy policy,
            ArrayList<MessagesController.DialogFilter> filters
    ) {
        return policy.showsAllChats() || findFallbackFilter(filters) < 0;
    }

    public static int findFallbackFilter(ArrayList<MessagesController.DialogFilter> filters) {
        for (int index = 0; index < filters.size(); index++) {
            MessagesController.DialogFilter filter = filters.get(index);
            if (!filter.isDefault() && !filter.locked) {
                return index;
            }
        }

        return -1;
    }

    private static int validatePresentationMode(int mode) {
        if (mode < MODE_TITLE_AND_ICON || mode > MODE_ICON) {
            return MODE_TITLE_AND_ICON;
        }

        return mode;
    }

    private static int validateCounterMode(int mode) {
        if (mode < COUNTER_ALL || mode > COUNTER_HIDDEN) {
            return COUNTER_ALL;
        }

        return mode;
    }

    private static synchronized SharedPreferences activePreferences(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0) {
            return null;
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_folders_" + account, Context.MODE_PRIVATE
        );
        if (preferences.getLong(OWNER, 0) != owner) {
            preferences.edit().clear().putLong(OWNER, owner).apply();
        }

        return preferences;
    }

    private FolderSettings() {
    }
}
