package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public final class FolderSettings {
    private static final String OWNER = "owner";
    private static final String PRESENTATION_MODE = "presentation_mode";
    private static final String COUNTER_MODE = "counter_mode";

    public static final int MODE_TITLE_AND_ICON = 0;
    public static final int MODE_TITLE = 1;
    public static final int MODE_ICON = 2;

    public static final int COUNTER_ALL = 0;
    public static final int COUNTER_UNMUTED = 1;
    public static final int COUNTER_HIDDEN = 2;

    public static final class Policy {
        private final int presentationMode;
        private final int counterMode;

        private Policy(int presentationMode, int counterMode) {
            this.presentationMode = presentationMode;
            this.counterMode = counterMode;
        }

        public int getPresentationMode() {
            return presentationMode;
        }

        public int getCounterMode() {
            return counterMode;
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

        return new Policy(
                validatePresentationMode(mode),
                validateCounterMode(counterMode)
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
