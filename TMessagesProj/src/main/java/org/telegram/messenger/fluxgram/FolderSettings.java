package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public final class FolderSettings {
    private static final String OWNER = "owner";
    private static final String PRESENTATION_MODE = "presentation_mode";

    public static final int MODE_TITLE_AND_ICON = 0;
    public static final int MODE_TITLE = 1;
    public static final int MODE_ICON = 2;

    public static final class Policy {
        private final int presentationMode;

        private Policy(int presentationMode) {
            this.presentationMode = presentationMode;
        }

        public int getPresentationMode() {
            return presentationMode;
        }
    }

    public static Policy getPolicy(int account) {
        SharedPreferences preferences = activePreferences(account);
        int mode = preferences == null
                ? MODE_TITLE_AND_ICON
                : preferences.getInt(PRESENTATION_MODE, MODE_TITLE_AND_ICON);

        return new Policy(validatePresentationMode(mode));
    }

    public static void setPresentationMode(int account, int mode) {
        SharedPreferences preferences = activePreferences(account);
        if (preferences == null || mode != validatePresentationMode(mode)) {
            return;
        }

        preferences.edit().putInt(PRESENTATION_MODE, mode).apply();
    }

    private static int validatePresentationMode(int mode) {
        if (mode < MODE_TITLE_AND_ICON || mode > MODE_ICON) {
            return MODE_TITLE_AND_ICON;
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
