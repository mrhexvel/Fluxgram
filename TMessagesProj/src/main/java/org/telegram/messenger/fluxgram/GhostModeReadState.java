package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class GhostModeReadState {
    private static final String OWNER = "owner";
    private static final String DIALOG_PREFIX = "dialog_";
    private static final String TOPIC_PREFIX = "topic_";
    private static final String CONTENT_PREFIX = "content_";
    private static final String HIDDEN_UNREAD_SUFFIX = "_hidden_unread";
    private static final String RETENTION_VERSION = "retention_version";
    private static final int CURRENT_RETENTION_VERSION = 1;
    private static final int CURSOR_LIMIT = 512;
    private static final int CONTENT_LIMIT = 2048;

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

    public static synchronized boolean recordContent(int account, MessageObject messageObject) {
        long dialogId = MessageObject.getDialogId(messageObject.messageOwner);
        if (!GhostModeSettings.shouldHideReadReceipts(account, dialogId)
                || !isEligibleContent(messageObject.messageOwner, account)) {
            return false;
        }

        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return false;
        }

        String key = contentKey(messageObject.messageOwner);
        SharedPreferences.Editor editor = preferences.edit();
        prepareInsert(preferences, editor, key, CONTENT_LIMIT, null, CONTENT_PREFIX);
        touch(preferences, editor, key);
        editor.putBoolean(key, true).apply();

        return true;
    }

    public static synchronized void applyContent(int account, TLRPC.Message message) {
        if (!message.media_unread || !isEligibleContent(message, account)) {
            return;
        }

        SharedPreferences preferences = activePreferences(account);

        if (preferences != null && preferences.getBoolean(contentKey(message), false)) {
            message.media_unread = false;
        }
    }

    public static synchronized void acknowledgeContent(int account, long dialogId, ArrayList<Integer> messageIds) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null || messageIds == null || messageIds.isEmpty()) {
            return;
        }

        long scope = dialogId < 0 ? dialogId : 0;
        SharedPreferences.Editor editor = preferences.edit();

        for (int messageId : messageIds) {
            if (messageId > 0) {
                remove(editor, contentKey(scope, messageId), false);
            }
        }

        editor.apply();
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
        putCursor(
                preferences,
                key,
                Math.max(currentMaxId, maxId),
                currentHiddenUnreadCount + Math.max(hiddenUnreadCount, 0)
        );
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

    public static synchronized int getDialogMax(int account, long dialogId) {
        return getMax(account, dialogKey(dialogId));
    }

    public static synchronized int getTopicMax(int account, long dialogId, long topicId) {
        return getMax(account, topicKey(dialogId, topicId));
    }

    public static synchronized void acknowledgeDialog(int account, long dialogId, int maxId) {
        acknowledge(account, dialogKey(dialogId), maxId);
    }

    public static synchronized void acknowledgeTopic(int account, long dialogId, long topicId, int maxId) {
        acknowledge(account, topicKey(dialogId, topicId), maxId);
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
            putCursor(preferences, key, maxId, preferences.getInt(hiddenUnreadKey(key), 0));
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
        putCursor(
                preferences,
                key,
                preferences.getInt(key, 0),
                currentHiddenUnreadCount + hiddenUnreadCount
        );
    }

    private static int getMax(int account, String key) {
        SharedPreferences preferences = activePreferences(account);

        return preferences == null ? 0 : preferences.getInt(key, 0);
    }

    private static void acknowledge(int account, String key, int maxId) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences == null) {
            return;
        }

        int localMaxId = preferences.getInt(key, 0);

        if (localMaxId > 0 && localMaxId <= maxId) {
            remove(preferences, key);
        }
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
        SharedPreferences.Editor editor = preferences.edit();
        remove(editor, key, true);
        editor.apply();
    }

    private static void putCursor(
            SharedPreferences preferences,
            String key,
            int maxId,
            int hiddenUnreadCount
    ) {
        SharedPreferences.Editor editor = preferences.edit();
        prepareInsert(
                preferences,
                editor,
                key,
                CURSOR_LIMIT,
                HIDDEN_UNREAD_SUFFIX,
                DIALOG_PREFIX,
                TOPIC_PREFIX
        );
        touch(preferences, editor, key);
        editor.putInt(key, maxId);

        if (hiddenUnreadCount > 0) {
            editor.putInt(hiddenUnreadKey(key), hiddenUnreadCount);
        } else {
            editor.remove(hiddenUnreadKey(key));
        }

        editor.apply();
    }

    private static void prepareInsert(
            SharedPreferences preferences,
            SharedPreferences.Editor editor,
            String key,
            int limit,
            String excludedSuffix,
            String... prefixes
    ) {
        if (preferences.contains(key)) {
            return;
        }

        String evicted = GhostModeStateRetention.evictionCandidate(
                preferences.getAll(),
                limit,
                excludedSuffix,
                prefixes
        );

        if (evicted != null) {
            remove(editor, evicted, excludedSuffix != null);
        }
    }

    private static void touch(
            SharedPreferences preferences,
            SharedPreferences.Editor editor,
            String key
    ) {
        long order = GhostModeStateRetention.nextOrder(
                preferences.getLong(GhostModeStateRetention.NEXT_ORDER, 0)
        );
        editor.putLong(GhostModeStateRetention.NEXT_ORDER, order);
        editor.putLong(GhostModeStateRetention.orderKey(key), order);
    }

    private static void remove(SharedPreferences.Editor editor, String key, boolean cursor) {
        editor.remove(key).remove(GhostModeStateRetention.orderKey(key));

        if (cursor) {
            editor.remove(hiddenUnreadKey(key));
        }
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

    private static boolean isEligibleContent(TLRPC.Message message, int account) {
        if (!(message instanceof TLRPC.TL_message) || message instanceof TLRPC.TL_message_secret || message.id <= 0 || message.saved_peer_id != null) {
            return false;
        }

        long dialogId = MessageObject.getDialogId(message);

        return dialogId != UserConfig.getInstance(account).getClientUserId() && !MessageObject.isSecretMedia(message);
    }

    private static String contentKey(TLRPC.Message message) {
        long scope = message.peer_id != null && message.peer_id.channel_id != 0 ? -message.peer_id.channel_id : 0;

        return contentKey(scope, message.id);
    }

    private static String contentKey(long scope, int messageId) {
        return CONTENT_PREFIX + scope + "_" + messageId;
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
                CURSOR_LIMIT,
                HIDDEN_UNREAD_SUFFIX,
                DIALOG_PREFIX,
                TOPIC_PREFIX
        )) {
            remove(editor, key, true);
        }

        for (String key : GhostModeStateRetention.excessEntries(
                preferences.getAll(),
                CONTENT_LIMIT,
                null,
                CONTENT_PREFIX
        )) {
            remove(editor, key, false);
        }

        editor.putInt(RETENTION_VERSION, CURRENT_RETENTION_VERSION).apply();
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_ghost_read_state_" + account, Context.MODE_PRIVATE
        );
    }
}
