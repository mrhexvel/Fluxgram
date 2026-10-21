package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class StickerSettings {
    private static final String SHOW_ALL_RECENT = "fluxgram_show_all_recent_stickers";

    private static volatile boolean loaded;
    private static volatile boolean showAllRecent;

    public static boolean showsAllRecent() {
        ensureLoaded();

        return showAllRecent;
    }

    public static synchronized void setShowsAllRecent(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_ALL_RECENT, enabled)
                .apply();

        showAllRecent = enabled;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (StickerSettings.class) {
            if (loaded) {
                return;
            }

            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            try {
                showAllRecent = preferences.getBoolean(SHOW_ALL_RECENT, false);
            } catch (ClassCastException exception) {
                showAllRecent = false;
            }
            loaded = true;
        }
    }

    private StickerSettings() {
    }
}
