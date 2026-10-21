package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class StickerSettings {
    private static final String SHOW_ALL_RECENT = "fluxgram_show_all_recent_stickers";
    private static final String SHOW_GREETING = "fluxgram_show_greeting_sticker";

    private static volatile boolean loaded;
    private static volatile boolean showAllRecent;
    private static volatile boolean showGreeting;

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

    public static boolean showsGreeting() {
        ensureLoaded();

        return showGreeting;
    }

    public static synchronized void setShowsGreeting(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_GREETING, enabled)
                .apply();

        showGreeting = enabled;
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
            try {
                showGreeting = preferences.getBoolean(SHOW_GREETING, true);
            } catch (ClassCastException exception) {
                showGreeting = true;
            }
            loaded = true;
        }
    }

    private StickerSettings() {
    }
}
