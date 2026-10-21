package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainMenuSettings {
    private static final String ORDER_KEY = "fluxgram_main_menu_order";
    private static final String HIDDEN_KEY = "fluxgram_main_menu_hidden";
    private static final String SEPARATOR = ",";

    private MainMenuSettings() {
    }

    public static MainMenuPolicy.State getState() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        MainMenuPolicy.State defaults = MainMenuPolicy.defaults();
        Collection<String> order = readIds(preferences, ORDER_KEY, defaults.getOrder());
        Collection<String> hidden = readIds(preferences, HIDDEN_KEY, defaults.getHidden());

        return MainMenuPolicy.normalize(order, hidden);
    }

    public static void setOrder(Collection<String> orderedIds) {
        MainMenuPolicy.State state = getState();
        save(MainMenuPolicy.normalize(orderedIds, state.getHidden()));
    }

    public static void setCommandVisible(String id, boolean visible) {
        if (!MainMenuPolicy.knownIds().contains(id) || MainMenuPolicy.isDivider(id)) {
            return;
        }

        MainMenuPolicy.State state = getState();
        Set<String> hidden = new LinkedHashSet<>(state.getHidden());
        if (visible) {
            hidden.remove(id);
        } else {
            hidden.add(id);
        }

        save(MainMenuPolicy.normalize(state.getOrder(), hidden));
    }

    public static void reset() {
        MessagesController.getGlobalMainSettings()
                .edit()
                .remove(ORDER_KEY)
                .remove(HIDDEN_KEY)
                .apply();
    }

    private static void save(MainMenuPolicy.State state) {
        MessagesController.getGlobalMainSettings()
                .edit()
                .putString(ORDER_KEY, encode(state.getOrder()))
                .putString(HIDDEN_KEY, encode(state.getHidden()))
                .apply();
    }

    private static String encode(Collection<String> ids) {
        StringBuilder value = new StringBuilder();
        for (String id : ids) {
            if (value.length() > 0) {
                value.append(SEPARATOR);
            }
            value.append(id);
        }

        return value.toString();
    }

    private static Collection<String> readIds(
            SharedPreferences preferences,
            String key,
            Collection<String> defaults
    ) {
        if (!preferences.contains(key)) {
            return defaults;
        }

        try {
            return decode(preferences.getString(key, ""));
        } catch (ClassCastException exception) {
            return defaults;
        }
    }

    private static List<String> decode(String value) {
        ArrayList<String> ids = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return ids;
        }

        String[] encodedIds = value.split(SEPARATOR, -1);
        for (String id : encodedIds) {
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }

        return ids;
    }
}
