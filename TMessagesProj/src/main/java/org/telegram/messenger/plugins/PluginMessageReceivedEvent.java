package org.telegram.messenger.plugins;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

public final class PluginMessageReceivedEvent {
    public final int account;
    public final long ownerId;
    public final int messageId;
    public final long dialogId;
    public final long topicId;
    public final Long senderId;
    public final String text;
    public final boolean outgoing;
    public final boolean mentioned;
    public final boolean reply;
    public final boolean silent;
    public final String chatTitle;
    public final String chatKind;
    public final Boolean chatForum;
    public final Boolean chatArchived;
    public final NotificationOverride chatOverride;
    public final NotificationOverride topicOverride;
    public final Boolean effectiveMuted;

    public static final class NotificationOverride {
        public final String mode;
        public final Integer muteUntil;

        NotificationOverride(String mode, Integer muteUntil) {
            this.mode = mode;
            this.muteUntil = muteUntil;
        }
    }

    private PluginMessageReceivedEvent(
            int account,
            long ownerId,
            int messageId,
            long dialogId,
            long topicId,
            Long senderId,
            String text,
            boolean outgoing,
            boolean mentioned,
            boolean reply,
            boolean silent,
            String chatTitle,
            String chatKind,
            Boolean chatForum,
            Boolean chatArchived,
            NotificationOverride chatOverride,
            NotificationOverride topicOverride,
            Boolean effectiveMuted) {
        this.account = account;
        this.ownerId = ownerId;
        this.messageId = messageId;
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.senderId = senderId;
        this.text = text;
        this.outgoing = outgoing;
        this.mentioned = mentioned;
        this.reply = reply;
        this.silent = silent;
        this.chatTitle = chatTitle;
        this.chatKind = chatKind;
        this.chatForum = chatForum;
        this.chatArchived = chatArchived;
        this.chatOverride = chatOverride;
        this.topicOverride = topicOverride;
        this.effectiveMuted = effectiveMuted;
    }

    public static PluginMessageReceivedEvent capture(int account, MessageObject message) {
        long ownerId = UserConfig.getInstance(account).getClientUserId();
        int messageId = message.getId();
        long dialogId = message.getDialogId();
        long topicId = message.getTopicId();
        long rawSenderId = message.getFromChatId();
        Long senderId = rawSenderId == 0 ? null : rawSenderId;
        String text = message.messageText == null ? "" : message.messageText.toString();
        boolean mentioned = message.messageOwner != null && message.messageOwner.mentioned;
        boolean silent = message.messageOwner != null && message.messageOwner.silent;

        MessagesController controller = MessagesController.getInstance(account);
        String chatTitle = null;
        String chatKind = "unknown";
        Boolean chatForum = null;

        if (DialogObject.isEncryptedDialog(dialogId)) {
            chatKind = "secret";
            chatForum = false;
        } else if (DialogObject.isUserDialog(dialogId)) {
            chatKind = "private";
            chatForum = false;

            TLRPC.User user = controller.getUser(dialogId);

            if (user != null) {
                String firstName = user.first_name == null ? "" : user.first_name;
                String lastName = user.last_name == null ? "" : user.last_name;
                String fullName = (firstName + " " + lastName).trim();
                chatTitle = fullName.isEmpty() ? null : fullName;
            }
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = controller.getChat(-dialogId);

            if (chat != null) {
                chatTitle = chat.title == null || chat.title.isEmpty() ? null : chat.title;
                boolean broadcast = ChatObject.isChannel(chat) && !chat.megagroup;
                chatKind = broadcast ? "channel" : "group";
                chatForum = ChatObject.isForum(chat);
            }
        }

        TLRPC.Dialog dialog = controller.getDialog(dialogId);
        Boolean chatArchived = dialog == null ? null : dialog.folder_id == 1;
        SharedPreferences preferences = MessagesController.getNotificationsSettings(account);
        NotificationOverride chatOverride = readOverride(preferences, dialogId, 0);
        NotificationOverride topicOverride = topicId == 0
                ? null
                : readOverride(preferences, dialogId, topicId);
        NotificationOverride selected = topicOverride != null
                && !"inherit".equals(topicOverride.mode)
                ? topicOverride
                : chatOverride;
        Boolean effectiveMuted;

        if ("unknown".equals(selected.mode)
                || "inherit".equals(selected.mode) && "unknown".equals(chatKind)) {
            effectiveMuted = null;
        } else {
            effectiveMuted = controller.isDialogMuted(dialogId, topicId);
        }

        return new PluginMessageReceivedEvent(
                account,
                ownerId,
                messageId,
                dialogId,
                topicId,
                senderId,
                text,
                message.isOut(),
                mentioned,
                message.isReply(),
                silent,
                chatTitle,
                chatKind,
                chatForum,
                chatArchived,
                chatOverride,
                topicOverride,
                effectiveMuted
        );
    }

    private static NotificationOverride readOverride(
            SharedPreferences preferences,
            long dialogId,
            long topicId) {
        String key = NotificationsController.getSharedPrefKey(dialogId, topicId, true);
        int value = preferences.getInt("notify2_" + key, -1);

        if (value == -1) {
            return new NotificationOverride("inherit", null);
        }

        if (value == 0 || value == 1) {
            return new NotificationOverride("enabled", null);
        }

        if (value == 2) {
            return new NotificationOverride("muted", null);
        }

        if (value == 3) {
            return new NotificationOverride(
                    "until",
                    preferences.getInt("notifyuntil_" + key, 0)
            );
        }

        return new NotificationOverride("unknown", null);
    }

    public String toJson() {
        try {
            return toJsonObject().toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not serialize message event", error);
        }
    }

    JSONObject toJsonObject() throws JSONException {
        JSONObject message = new JSONObject();
        message.put("id", messageId);
        message.put("dialog_id", dialogId);
        message.put("topic_id", topicId);
        putNullable(message, "sender_id", senderId);
        message.put("text", text);
        message.put("outgoing", outgoing);
        message.put("mentioned", mentioned);
        message.put("is_reply", reply);
        message.put("silent", silent);

        JSONObject notifications = new JSONObject();
        notifications.put("chat_override", overrideToJson(chatOverride));
        putNullable(
                notifications,
                "topic_override",
                topicOverride == null ? null : overrideToJson(topicOverride)
        );
        putNullable(notifications, "effective_muted", effectiveMuted);

        JSONObject chat = new JSONObject();
        chat.put("id", dialogId);
        putNullable(chat, "title", chatTitle);
        chat.put("kind", chatKind);
        putNullable(chat, "is_forum", chatForum);
        putNullable(chat, "archived", chatArchived);
        chat.put("notifications", notifications);

        JSONObject result = new JSONObject();
        result.put("chat", chat);
        result.put("message", message);

        return result;
    }

    private static JSONObject overrideToJson(NotificationOverride override)
            throws JSONException {
        JSONObject result = new JSONObject();
        result.put("mode", override.mode);
        putNullable(result, "mute_until", override.muteUntil);

        return result;
    }

    private static void putNullable(JSONObject object, String key, Object value)
            throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }
}
