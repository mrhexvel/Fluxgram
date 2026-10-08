package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

public final class GhostModeReadState {
    private static final String OWNER = "owner";
    private static final String DIALOG_PREFIX = "dialog_";
    private static final String TOPIC_PREFIX = "topic_";
    private static final String HIDDEN_UNREAD_SUFFIX = "_hidden_unread";

    private GhostModeReadState() {
    }

    public static synchronized void recordDialog(int account, long dialogId, int maxId) {
        record(account, dialogKey(dialogId), maxId);
    }

    public static synchronized void addDialogHiddenUnreadCount(int account, long dialogId, int hiddenUnreadCount) {
        addHiddenUnreadCount(account, dialogKey(dialogId), hiddenUnreadCount);
    }

    public static synchronized void recordTopic(int account, long dialogId, long topicId, int maxId) {
        record(account, topicKey(dialogId, topicId), maxId);
    }

    public static synchronized void preserveTopicRead(int account, long dialogId, long topicId, int maxId, int hiddenUnreadCount, boolean allowParentCursor) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return;
        }

        String key = topicKey(dialogId, topicId);

        if (!preferences.contains(key) && (!allowParentCursor || !preferences.contains(dialogKey(dialogId)))) {
            return;
        }

        int currentMaxId = preferences.getInt(key, 0);
        int currentHiddenUnreadCount = preferences.getInt(hiddenUnreadKey(key), 0);
        preferences.edit()
                .putInt(key, Math.max(currentMaxId, maxId))
                .putInt(hiddenUnreadKey(key), currentHiddenUnreadCount + Math.max(hiddenUnreadCount, 0))
                .apply();
    }

    public static synchronized void applyDialog(int account, TLRPC.Dialog dialog, boolean serverData) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return;
        }

        String key = dialogKey(dialog.id);
        int localMaxId = preferences.getInt(key, 0);

        if (localMaxId <= 0) {
            return;
        }

        if (serverData && dialog.read_inbox_max_id >= localMaxId) {
            remove(preferences, key);
            return;
        }

        if (dialog.read_inbox_max_id < localMaxId) {
            dialog.read_inbox_max_id = localMaxId;
            dialog.unread_count = mergeUnreadCount(preferences, key, dialog.top_message, localMaxId, dialog.unread_count);
            dialog.unread_mark = false;
        }
    }

    public static synchronized void applyTopic(int account, long dialogId, long topicId, TLRPC.TL_forumTopic topic, boolean serverData) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return;
        }

        String key = topicKey(dialogId, topicId);
        int localMaxId = preferences.getInt(key, 0);

        if (localMaxId <= 0) {
            return;
        }

        if (serverData && topic.read_inbox_max_id >= localMaxId) {
            remove(preferences, key);
            return;
        }

        if (topic.read_inbox_max_id < localMaxId) {
            topic.read_inbox_max_id = localMaxId;
            topic.unread_count = mergeUnreadCount(preferences, key, topic.top_message, localMaxId, topic.unread_count);
        }
    }

    public static synchronized int mergeDialogMax(int account, long dialogId, int serverMaxId) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return serverMaxId;
        }

        String key = dialogKey(dialogId);
        int localMaxId = preferences.getInt(key, 0);

        if (localMaxId <= 0) {
            return serverMaxId;
        }

        if (serverMaxId >= localMaxId) {
            remove(preferences, key);
            return serverMaxId;
        }

        return localMaxId;
    }

    public static synchronized int mergeDialogUnreadCount(int account, long dialogId, int topMessageId, int localMaxId, int serverUnreadCount) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return serverUnreadCount;
        }

        return mergeUnreadCount(preferences, dialogKey(dialogId), topMessageId, localMaxId, serverUnreadCount);
    }

    public static synchronized boolean hasDialogCursor(int account, long dialogId) {
        SharedPreferences preferences = activePreferences(account);

        return preferences != null && preferences.getInt(dialogKey(dialogId), 0) > 0;
    }

    public static synchronized void clearAccount(int account) {
        preferences(account).edit().clear().apply();
    }

    private static void record(int account, String key, int maxId) {
        if (maxId <= 0) {
            return;
        }

        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return;
        }

        int currentMaxId = preferences.getInt(key, 0);

        if (maxId > currentMaxId) {
            preferences.edit().putInt(key, maxId).apply();
        }
    }

    private static void addHiddenUnreadCount(int account, String key, int hiddenUnreadCount) {
        if (hiddenUnreadCount <= 0) {
            return;
        }

        SharedPreferences preferences = activePreferences(account);

        if (preferences == null || !preferences.contains(key)) {
            return;
        }

        int currentHiddenUnreadCount = preferences.getInt(hiddenUnreadKey(key), 0);
        preferences.edit().putInt(hiddenUnreadKey(key), currentHiddenUnreadCount + hiddenUnreadCount).apply();
    }

    private static int mergeUnreadCount(SharedPreferences preferences, String key, int topMessageId, int localMaxId, int unreadCount) {
        int hiddenUnreadCount = preferences.getInt(hiddenUnreadKey(key), 0);

        if (hiddenUnreadCount > 0) {
            return Math.max(unreadCount - hiddenUnreadCount, 0);
        }

        if (topMessageId <= localMaxId) {
            return 0;
        }

        return unreadCount;
    }

    private static void remove(SharedPreferences preferences, String key) {
        preferences.edit().remove(key).remove(hiddenUnreadKey(key)).apply();
    }

    private static String hiddenUnreadKey(String key) {
        return key + HIDDEN_UNREAD_SUFFIX;
    }

    private static String dialogKey(long dialogId) {
        return DIALOG_PREFIX + dialogId;
    }

    private static String topicKey(long dialogId, long topicId) {
        return TOPIC_PREFIX + dialogId + "_" + topicId;
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

        return preferences;
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_ghost_read_state_" + account, Context.MODE_PRIVATE
        );
    }
}
