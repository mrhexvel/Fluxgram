package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class MessageGestureSettings {
    private static final int CURRENT_VERSION = 1;
    private static final String VERSION = "fluxgram_message_gestures_version";
    private static final String DOUBLE_TAP_ENABLED = "fluxgram_message_double_tap_enabled";
    private static final String DOUBLE_TAP_ACTION = "fluxgram_message_double_tap_action";

    private static volatile State state;

    public static State getState() {
        State current = state;
        if (current != null) {
            return current;
        }

        synchronized (MessageGestureSettings.class) {
            if (state == null) {
                state = load();
            }

            return state;
        }
    }

    public static synchronized void setDoubleTapEnabled(boolean enabled) {
        State current = getState();
        save(new State(enabled, current.getDoubleTapAction()));
    }

    public static synchronized void setDoubleTapAction(String actionId) {
        State current = getState();
        save(new State(current.isDoubleTapEnabled(), normalizeDoubleTapAction(actionId)));
    }

    private static State load() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        int version;
        boolean enabled;
        String actionId;
        boolean canonical = true;

        try {
            version = preferences.getInt(VERSION, 0);
        } catch (ClassCastException exception) {
            version = 0;
        }
        try {
            enabled = preferences.getBoolean(DOUBLE_TAP_ENABLED, true);
        } catch (ClassCastException exception) {
            enabled = true;
            canonical = false;
        }
        try {
            actionId = preferences.getString(
                    DOUBLE_TAP_ACTION,
                    MessageActionExecutor.REACT_DEFAULT
            );
        } catch (ClassCastException exception) {
            actionId = MessageActionExecutor.REACT_DEFAULT;
            canonical = false;
        }

        String normalizedAction = normalizeDoubleTapAction(actionId);
        State loaded = new State(enabled, normalizedAction);
        if (version != CURRENT_VERSION || !normalizedAction.equals(actionId) || !canonical) {
            persist(loaded);
        }

        return loaded;
    }

    private static void save(State value) {
        persist(value);
        state = value;
    }

    private static void persist(State value) {
        MessagesController.getGlobalMainSettings()
                .edit()
                .putInt(VERSION, CURRENT_VERSION)
                .putBoolean(DOUBLE_TAP_ENABLED, value.isDoubleTapEnabled())
                .putString(DOUBLE_TAP_ACTION, value.getDoubleTapAction())
                .apply();
    }

    private static String normalizeDoubleTapAction(String actionId) {
        if (MessageActionExecutor.REPLY.equals(actionId)) {
            return MessageActionExecutor.REPLY;
        }
        if (MessageActionExecutor.COPY.equals(actionId)) {
            return MessageActionExecutor.COPY;
        }

        return MessageActionExecutor.REACT_DEFAULT;
    }

    public static final class State {
        private final boolean doubleTapEnabled;
        private final String doubleTapAction;

        private State(boolean doubleTapEnabled, String doubleTapAction) {
            this.doubleTapEnabled = doubleTapEnabled;
            this.doubleTapAction = doubleTapAction;
        }

        public boolean isDoubleTapEnabled() {
            return doubleTapEnabled;
        }

        public String getDoubleTapAction() {
            return doubleTapAction;
        }
    }

    private MessageGestureSettings() {
    }
}
