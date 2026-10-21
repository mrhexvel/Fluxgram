package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public final class MessageGestureSettings {
    private static final int CURRENT_VERSION = 2;
    private static final String VERSION = "fluxgram_message_gestures_version";
    private static final String DOUBLE_TAP_ENABLED = "fluxgram_message_double_tap_enabled";
    private static final String DOUBLE_TAP_ACTION = "fluxgram_message_double_tap_action";
    private static final String SWIPE_ENABLED = "fluxgram_message_swipe_enabled";
    private static final String SWIPE_ACTIONS = "fluxgram_message_swipe_actions";

    private static final List<String> PUBLISHED_SWIPE_ACTIONS = Collections.unmodifiableList(
            Arrays.asList(
                    MessageActionExecutor.REPLY,
                    MessageActionExecutor.FORWARD
            )
    );

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
        save(new State(
                enabled,
                current.getDoubleTapAction(),
                current.isSwipeEnabled(),
                current.getSwipeActions()
        ));
    }

    public static synchronized void setDoubleTapAction(String actionId) {
        State current = getState();
        save(new State(
                current.isDoubleTapEnabled(),
                normalizeDoubleTapAction(actionId),
                current.isSwipeEnabled(),
                current.getSwipeActions()
        ));
    }

    public static synchronized void setSwipeEnabled(boolean enabled) {
        State current = getState();
        save(new State(
                current.isDoubleTapEnabled(),
                current.getDoubleTapAction(),
                enabled,
                current.getSwipeActions()
        ));
    }

    public static synchronized void setSwipeActions(Collection<String> actionIds) {
        State current = getState();
        save(new State(
                current.isDoubleTapEnabled(),
                current.getDoubleTapAction(),
                current.isSwipeEnabled(),
                normalizeSwipeActions(actionIds)
        ));
    }

    public static List<String> publishedSwipeActions() {
        return PUBLISHED_SWIPE_ACTIONS;
    }

    private static State load() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        int version;
        boolean enabled;
        String actionId;
        boolean swipeEnabled;
        String swipeActions;
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
        try {
            swipeEnabled = preferences.getBoolean(SWIPE_ENABLED, true);
        } catch (ClassCastException exception) {
            swipeEnabled = true;
            canonical = false;
        }
        try {
            swipeActions = preferences.getString(SWIPE_ACTIONS, MessageActionExecutor.REPLY);
        } catch (ClassCastException exception) {
            swipeActions = MessageActionExecutor.REPLY;
            canonical = false;
        }

        String normalizedAction = normalizeDoubleTapAction(actionId);
        List<String> normalizedSwipeActions = normalizeSwipeActions(parseSwipeActions(swipeActions));
        State loaded = new State(
                enabled,
                normalizedAction,
                swipeEnabled,
                normalizedSwipeActions
        );
        if (version != CURRENT_VERSION
                || !normalizedAction.equals(actionId)
                || !encodeSwipeActions(normalizedSwipeActions).equals(swipeActions)
                || !canonical) {
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
                .putBoolean(SWIPE_ENABLED, value.isSwipeEnabled())
                .putString(SWIPE_ACTIONS, encodeSwipeActions(value.getSwipeActions()))
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

    private static List<String> parseSwipeActions(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }

        String[] actionIds = value.split(",", -1);
        ArrayList<String> result = new ArrayList<>(actionIds.length);
        Collections.addAll(result, actionIds);
        return result;
    }

    private static List<String> normalizeSwipeActions(Collection<String> actionIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (actionIds != null) {
            for (String actionId : actionIds) {
                if (PUBLISHED_SWIPE_ACTIONS.contains(actionId)) {
                    normalized.add(actionId);
                }
            }
        }
        if (normalized.isEmpty()) {
            return PUBLISHED_SWIPE_ACTIONS;
        }

        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    private static String encodeSwipeActions(List<String> actionIds) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < actionIds.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(actionIds.get(index));
        }

        return result.toString();
    }

    public static final class State {
        private final boolean doubleTapEnabled;
        private final String doubleTapAction;
        private final boolean swipeEnabled;
        private final List<String> swipeActions;

        private State(
                boolean doubleTapEnabled,
                String doubleTapAction,
                boolean swipeEnabled,
                List<String> swipeActions
        ) {
            this.doubleTapEnabled = doubleTapEnabled;
            this.doubleTapAction = doubleTapAction;
            this.swipeEnabled = swipeEnabled;
            this.swipeActions = swipeActions;
        }

        public boolean isDoubleTapEnabled() {
            return doubleTapEnabled;
        }

        public String getDoubleTapAction() {
            return doubleTapAction;
        }

        public boolean isSwipeEnabled() {
            return swipeEnabled;
        }

        public List<String> getSwipeActions() {
            return swipeActions;
        }
    }

    private MessageGestureSettings() {
    }
}
