package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class StickerSettings {
    private static final String SHOW_ALL_RECENT = "fluxgram_show_all_recent_stickers";
    private static final String SHOW_GREETING = "fluxgram_show_greeting_sticker";
    private static final String SHOW_STICKER_TIME = "fluxgram_show_sticker_time";
    private static final String STICKER_TIME_MODE = "fluxgram_sticker_time_mode";
    private static final String STICKER_SIZE = "fluxgram_sticker_size";
    private static final String STICKER_SHAPE = "fluxgram_sticker_shape";

    public static final int TIME_DEFAULT = 0;
    public static final int TIME_SIDE = 1;
    public static final int TIME_HIDDEN = 2;

    public static final int MIN_STICKER_SIZE = 4;
    public static final int DEFAULT_STICKER_SIZE = 12;
    public static final int MAX_STICKER_SIZE = 20;

    public static final int SHAPE_DEFAULT = 0;
    public static final int SHAPE_ROUNDED = 1;
    public static final int SHAPE_MESSAGE = 2;
    private static final int DEFAULT_STICKER_SHAPE = SHAPE_DEFAULT;

    private static volatile boolean loaded;
    private static volatile boolean showAllRecent;
    private static volatile boolean showGreeting;
    private static volatile int stickerTimeMode;
    private static volatile int stickerSize;
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
        return getStickerTimeMode() != TIME_HIDDEN;
    }

    public static synchronized void setShowsStickerTime(boolean enabled) {
        setStickerTimeMode(enabled ? TIME_DEFAULT : TIME_HIDDEN);
    }

    public static int getStickerTimeMode() {
        ensureLoaded();

        return stickerTimeMode;
    }

    public static synchronized void setStickerTimeMode(int mode) {
        if (mode < TIME_DEFAULT || mode > TIME_HIDDEN) {
            return;
        }

        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putInt(STICKER_TIME_MODE, mode)
                .apply();

        stickerTimeMode = mode;
    }

    public static int getStickerSize() {
        ensureLoaded();

        return stickerSize;
    }

    public static synchronized void setStickerSize(int size) {
        size = Math.max(MIN_STICKER_SIZE, Math.min(MAX_STICKER_SIZE, size));
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putInt(STICKER_SIZE, size)
                .apply();

        stickerSize = size;
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
                stickerTimeMode = preferences.getInt(STICKER_TIME_MODE, -1);
            } catch (ClassCastException exception) {
                stickerTimeMode = -1;
            }
            if (stickerTimeMode < TIME_DEFAULT || stickerTimeMode > TIME_HIDDEN) {
                try {
                    stickerTimeMode = preferences.getBoolean(SHOW_STICKER_TIME, true)
                            ? TIME_DEFAULT
                            : TIME_HIDDEN;
                } catch (ClassCastException exception) {
                    stickerTimeMode = TIME_DEFAULT;
                }
            }
            try {
                stickerSize = preferences.getInt(STICKER_SIZE, DEFAULT_STICKER_SIZE);
            } catch (ClassCastException exception) {
                stickerSize = DEFAULT_STICKER_SIZE;
            }
            if (stickerSize < MIN_STICKER_SIZE || stickerSize > MAX_STICKER_SIZE) {
                stickerSize = DEFAULT_STICKER_SIZE;
            }
            try {
                stickerShape = preferences.getInt(STICKER_SHAPE, DEFAULT_STICKER_SHAPE);
            } catch (ClassCastException exception) {
                stickerShape = DEFAULT_STICKER_SHAPE;
            }
            if (stickerShape < SHAPE_DEFAULT || stickerShape > SHAPE_MESSAGE) {
                stickerShape = DEFAULT_STICKER_SHAPE;
            }
            loaded = true;
        }
    }

    private StickerSettings() {
    }
}
