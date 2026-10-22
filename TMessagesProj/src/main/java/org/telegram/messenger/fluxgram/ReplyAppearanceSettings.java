package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class ReplyAppearanceSettings {
    private static final String USE_PEER_COLOR = "fluxgram_reply_use_peer_color";
    private static final String SHOW_BACKGROUND_EMOJI = "fluxgram_reply_show_background_emoji";
    private static final String SHOW_BACKGROUND = "fluxgram_reply_show_background";

    private static volatile State state;

    public static State getState() {
        State current = state;
        if (current != null) {
            return current;
        }

        synchronized (ReplyAppearanceSettings.class) {
            current = state;
            if (current == null) {
                current = readState(MessagesController.getGlobalMainSettings());
                state = current;
            }
        }

        return current;
    }

    public static synchronized void setUsePeerColor(boolean enabled) {
        State current = getState();
        persist(USE_PEER_COLOR, enabled);
        state = new State(enabled, current.showsBackgroundEmoji(), current.showsBackground());
    }

    public static synchronized void setShowBackgroundEmoji(boolean enabled) {
        State current = getState();
        persist(SHOW_BACKGROUND_EMOJI, enabled);
        state = new State(current.usesPeerColor(), enabled, current.showsBackground());
    }

    public static synchronized void setShowBackground(boolean enabled) {
        State current = getState();
        persist(SHOW_BACKGROUND, enabled);
        state = new State(current.usesPeerColor(), current.showsBackgroundEmoji(), enabled);
    }

    public static synchronized void setAll(boolean enabled) {
        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(USE_PEER_COLOR, enabled)
                .putBoolean(SHOW_BACKGROUND_EMOJI, enabled)
                .putBoolean(SHOW_BACKGROUND, enabled)
                .apply();
        state = new State(enabled, enabled, enabled);
    }

    private static State readState(SharedPreferences preferences) {
        return new State(
                readBoolean(preferences, USE_PEER_COLOR),
                readBoolean(preferences, SHOW_BACKGROUND_EMOJI),
                readBoolean(preferences, SHOW_BACKGROUND)
        );
    }

    private static boolean readBoolean(SharedPreferences preferences, String key) {
        try {
            return preferences.getBoolean(key, true);
        } catch (ClassCastException exception) {
            return true;
        }
    }

    private static void persist(String key, boolean value) {
        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(key, value)
                .apply();
    }

    public static final class State {
        private final boolean usePeerColor;
        private final boolean showBackgroundEmoji;
        private final boolean showBackground;

        private State(boolean usePeerColor, boolean showBackgroundEmoji, boolean showBackground) {
            this.usePeerColor = usePeerColor;
            this.showBackgroundEmoji = showBackgroundEmoji;
            this.showBackground = showBackground;
        }

        public boolean usesPeerColor() {
            return usePeerColor;
        }

        public boolean showsBackgroundEmoji() {
            return showBackgroundEmoji;
        }

        public boolean showsBackground() {
            return showBackground;
        }
    }

    private ReplyAppearanceSettings() {
    }
}
