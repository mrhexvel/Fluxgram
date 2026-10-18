package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public final class GhostModeStoryState {
    private static final String OWNER = "owner";
    private static final String ACTIVE_PREFIX = "active_";
    private static final String SEEN_PREFIX = "seen_";
    private static final String RETENTION_VERSION = "retention_version";
    private static final int CURRENT_RETENTION_VERSION = 1;
    private static final int ACTIVE_LIMIT = 512;
    private static final int SEEN_LIMIT = 2048;

    private GhostModeStoryState() {
    }

    public static synchronized boolean recordActive(int account, long dialogId, int storyId) {
        if (dialogId == 0 || storyId <= 0) {
            return false;
        }

        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return false;
        }

        String key = activeKey(dialogId);
        int currentStoryId = preferences.getInt(key, 0);

        if (storyId <= currentStoryId) {
            return false;
        }

        preparePut(preferences, key, ACTIVE_LIMIT, ACTIVE_PREFIX)
                .putInt(key, storyId)
                .apply();

        return true;
    }

    public static synchronized int mergeActiveMax(int account, long dialogId, int serverMaxId) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return serverMaxId;
        }

        return Math.max(serverMaxId, preferences.getInt(activeKey(dialogId), 0));
    }

    public static synchronized void acknowledgeActive(int account, long dialogId, int serverMaxId) {
        if (serverMaxId <= 0) {
            return;
        }

        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return;
        }

        String key = activeKey(dialogId);
        int localMaxId = preferences.getInt(key, 0);

        if (localMaxId > 0 && serverMaxId >= localMaxId) {
            remove(preferences, key);
        }
    }

    public static synchronized boolean recordSeen(int account, long dialogId, int storyId) {
        if (dialogId == 0 || storyId <= 0) {
            return false;
        }

        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return false;
        }

        String key = seenKey(dialogId, storyId);

        if (preferences.getBoolean(key, false)) {
            return false;
        }

        preparePut(preferences, key, SEEN_LIMIT, SEEN_PREFIX)
                .putBoolean(key, true)
                .apply();

        return true;
    }

    public static synchronized boolean isSeen(int account, long dialogId, int storyId) {
        SharedPreferences preferences = activePreferences(account);

        return preferences != null && preferences.getBoolean(seenKey(dialogId, storyId), false);
    }

    public static synchronized void clearAccount(int account) {
        preferences(account).edit().clear().apply();
    }

    private static String activeKey(long dialogId) {
        return ACTIVE_PREFIX + dialogId;
    }

    private static String seenKey(long dialogId, int storyId) {
        return SEEN_PREFIX + dialogId + "_" + storyId;
    }

    private static SharedPreferences.Editor preparePut(
            SharedPreferences preferences,
            String key,
            int limit,
            String prefix
    ) {
        SharedPreferences.Editor editor = preferences.edit();
        String evicted = preferences.contains(key) ? null : GhostModeStateRetention.evictionCandidate(
                preferences.getAll(),
                limit,
                null,
                prefix
        );

        if (evicted != null) {
            editor.remove(evicted).remove(GhostModeStateRetention.orderKey(evicted));
        }

        long order = GhostModeStateRetention.nextOrder(
                preferences.getLong(GhostModeStateRetention.NEXT_ORDER, 0)
        );
        editor.putLong(GhostModeStateRetention.NEXT_ORDER, order);
        editor.putLong(GhostModeStateRetention.orderKey(key), order);
        return editor;
    }

    private static void remove(SharedPreferences preferences, String key) {
        preferences.edit()
                .remove(key)
                .remove(GhostModeStateRetention.orderKey(key))
                .apply();
    }

    private static synchronized SharedPreferences activePreferences(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();

        if (owner == 0) {
            return null;
        }

        SharedPreferences preferences = preferences(account);

        if (preferences.getLong(OWNER, 0) != owner) {
            preferences.edit().clear().putLong(OWNER, owner).apply();
        }

        normalizeRetention(preferences);

        return preferences;
    }

    private static void normalizeRetention(SharedPreferences preferences) {
        if (preferences.getInt(RETENTION_VERSION, 0) >= CURRENT_RETENTION_VERSION) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();

        for (String key : GhostModeStateRetention.excessEntries(
                preferences.getAll(),
                ACTIVE_LIMIT,
                null,
                ACTIVE_PREFIX
        )) {
            editor.remove(key).remove(GhostModeStateRetention.orderKey(key));
        }

        for (String key : GhostModeStateRetention.excessEntries(
                preferences.getAll(),
                SEEN_LIMIT,
                null,
                SEEN_PREFIX
        )) {
            editor.remove(key).remove(GhostModeStateRetention.orderKey(key));
        }

        editor.putInt(RETENTION_VERSION, CURRENT_RETENTION_VERSION).apply();
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_ghost_story_state_" + account, Context.MODE_PRIVATE
        );
    }
}
