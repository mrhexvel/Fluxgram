package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.SparseArray;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserConfig;

import java.util.HashSet;
import java.util.Set;

public final class GhostModeDialogScope {
    private static final String OWNER = "owner";
    private static final String WHITELIST = "whitelist";
    private static final String BLACKLIST = "blacklist";

    private static final SparseArray<State> states = new SparseArray<>();

    private GhostModeDialogScope() {
    }

    public static synchronized HashSet<Long> whitelist(int account) {
        State state = activeState(account);
        return state == null ? new HashSet<>() : new HashSet<>(state.whitelist);
    }

    public static synchronized void setWhitelist(int account, Set<Long> dialogs) {
        State state = activeState(account);
        if (state == null) {
            return;
        }

        state.whitelist.clear();
        state.whitelist.addAll(normalize(account, dialogs));
        state.blacklist.removeAll(state.whitelist);
        persist(account, state);
    }

    public static synchronized HashSet<Long> blacklist(int account) {
        State state = activeState(account);
        return state == null ? new HashSet<>() : new HashSet<>(state.blacklist);
    }

    public static synchronized void setBlacklist(int account, Set<Long> dialogs) {
        State state = activeState(account);
        if (state == null) {
            return;
        }

        state.blacklist.clear();
        state.blacklist.addAll(normalize(account, dialogs));
        state.whitelist.removeAll(state.blacklist);
        persist(account, state);
    }

    public static synchronized boolean shouldApply(int account, long dialogId) {
        State state = activeState(account);
        if (state == null || !validDialog(account, dialogId) || state.whitelist.contains(dialogId)) {
            return false;
        }
        if (state.blacklist.contains(dialogId)) {
            return true;
        }

        return GhostModeSettings.isEnabled(account);
    }

    public static synchronized void clearAccount(int account) {
        states.remove(account);
        preferences(account).edit().clear().apply();
    }

    private static State activeState(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0) {
            states.remove(account);
            return null;
        }

        State state = states.get(account);
        if (state != null && state.owner == owner) {
            return state;
        }

        SharedPreferences preferences = preferences(account);
        if (preferences.getLong(OWNER, 0) != owner) {
            preferences.edit().clear().putLong(OWNER, owner).apply();
            state = new State(owner, new HashSet<>(), new HashSet<>());
        } else {
            state = new State(
                    owner,
                    readDialogs(account, preferences, WHITELIST),
                    readDialogs(account, preferences, BLACKLIST)
            );
            state.blacklist.removeAll(state.whitelist);
        }

        states.put(account, state);
        return state;
    }

    private static HashSet<Long> normalize(int account, Set<Long> dialogs) {
        HashSet<Long> result = new HashSet<>();
        if (dialogs == null) {
            return result;
        }

        for (Long dialogId : dialogs) {
            if (dialogId != null && validDialog(account, dialogId)) {
                result.add(dialogId);
            }
        }

        return result;
    }

    private static HashSet<Long> readDialogs(int account, SharedPreferences preferences, String key) {
        HashSet<Long> result = new HashSet<>();

        for (String value : preferences.getStringSet(key, new HashSet<>())) {
            try {
                long dialogId = Long.parseLong(value);
                if (validDialog(account, dialogId)) {
                    result.add(dialogId);
                }
            } catch (NumberFormatException ignore) {
            }
        }

        return result;
    }

    private static boolean validDialog(int account, long dialogId) {
        return dialogId != UserConfig.getInstance(account).getClientUserId()
                && (DialogObject.isUserDialog(dialogId) || DialogObject.isChatDialog(dialogId));
    }

    private static void persist(int account, State state) {
        preferences(account).edit()
                .putStringSet(WHITELIST, strings(state.whitelist))
                .putStringSet(BLACKLIST, strings(state.blacklist))
                .apply();
    }

    private static HashSet<String> strings(Set<Long> dialogs) {
        HashSet<String> result = new HashSet<>();

        for (long dialogId : dialogs) {
            result.add(Long.toString(dialogId));
        }

        return result;
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_ghost_mode_dialogs_" + account,
                Context.MODE_PRIVATE
        );
    }

    private static final class State {
        private final long owner;
        private final HashSet<Long> whitelist;
        private final HashSet<Long> blacklist;

        private State(long owner, HashSet<Long> whitelist, HashSet<Long> blacklist) {
            this.owner = owner;
            this.whitelist = whitelist;
            this.blacklist = blacklist;
        }
    }
}
