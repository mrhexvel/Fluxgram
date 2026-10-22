package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class StickerSettings {
    private static final String SHOW_ALL_RECENT = "fluxgram_show_all_recent_stickers";
    private static final String SHOW_GREETING = "fluxgram_show_greeting_sticker";
    private static final String SHOW_STICKER_TIME = "fluxgram_show_sticker_time";
    private static final String STICKER_SHAPE = "fluxgram_sticker_shape";

    public static final int SHAPE_DEFAULT = 0;
    public static final int SHAPE_ROUNDED = 1;
    public static final int SHAPE_MESSAGE = 2;

    private static volatile boolean loaded;
    private static volatile boolean showAllRecent;
    private static volatile boolean showGreeting;
    private static volatile boolean showStickerTime;
    private static volatile int stickerShape;

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

    public static boolean showsStickerTime() {
        ensureLoaded();

        return showStickerTime;
    }

    public static synchronized void setShowsStickerTime(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_STICKER_TIME, enabled)
                .apply();

        showStickerTime = enabled;
    }

    public static int getStickerShape() {
        ensureLoaded();

        return stickerShape;
    }

    public static synchronized void setStickerShape(int shape) {
        if (shape < SHAPE_DEFAULT || shape > SHAPE_MESSAGE) {
            return;
        }

        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putInt(STICKER_SHAPE, shape)
                .apply();

        stickerShape = shape;
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
            try {
                showStickerTime = preferences.getBoolean(SHOW_STICKER_TIME, true);
            } catch (ClassCastException exception) {
                showStickerTime = true;
            }
            try {
                stickerShape = preferences.getInt(STICKER_SHAPE, SHAPE_DEFAULT);
            } catch (ClassCastException exception) {
                stickerShape = SHAPE_DEFAULT;
            }
            if (stickerShape < SHAPE_DEFAULT || stickerShape > SHAPE_MESSAGE) {
                stickerShape = SHAPE_DEFAULT;
            }
            loaded = true;
        }
    }

    private StickerSettings() {
    }
}
