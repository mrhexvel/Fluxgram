package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

final class PluginMessageEvents implements NotificationCenter.NotificationCenterDelegate {
    private static final PluginMessageEvents INSTANCE = new PluginMessageEvents();
    private boolean initialized;

    private PluginMessageEvents() {}

    static synchronized void initialize() {
        if (INSTANCE.initialized) {
            return;
        }

        INSTANCE.initialized = true;

        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            NotificationCenter.getInstance(account).addObserver(
                    INSTANCE,
                    NotificationCenter.didReceiveNewMessages
            );
            NotificationCenter.getInstance(account).addObserver(
                    INSTANCE,
                    NotificationCenter.fluxgramMessageEdited
            );
            NotificationCenter.getInstance(account).addObserver(
                    INSTANCE,
                    NotificationCenter.fluxgramMessagesDeleted
            );
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            messageReceived(account, args);
        } else if (id == NotificationCenter.fluxgramMessageEdited) {
            messageEdited(account, args);
        } else if (id == NotificationCenter.fluxgramMessagesDeleted) {
            messagesDeleted(account, args);
        }
    }

    private void messageReceived(int account, Object... args) {
        if (args.length < 4
                || Boolean.TRUE.equals(args[2])
                || !(args[3] instanceof Integer)
                || (Integer) args[3] != ChatActivity.MODE_DEFAULT
                || !(args[1] instanceof ArrayList)) {
            return;
        }

        PluginManager manager = PluginManager.getInstance();

        if (!manager.hasMessageReceivedPlugins()) {
            return;
        }

        ArrayList<?> messages = (ArrayList<?>) args[1];

        for (Object value : messages) {
            if (!(value instanceof MessageObject)) {
                continue;
            }

            MessageObject message = (MessageObject) value;

            if (message.isOut()) {
                continue;
            }

            try {
                manager.messageReceived(
                        PluginMessageReceivedEvent.capture(account, message)
                );
            } catch (Exception | LinkageError error) {
                FileLog.e("FluxGram message event snapshot failed", error);
            }
        }
    }

    private void messageEdited(int account, Object... args) {
        if (args.length < 2 || !(args[1] instanceof ArrayList)) {
            return;
        }

        PluginManager manager = PluginManager.getInstance();

        if (!manager.hasMessageEditedPlugins()) {
            return;
        }

        ArrayList<?> messages = (ArrayList<?>) args[1];

        for (Object value : messages) {
            if (!(value instanceof MessageObject)) {
                continue;
            }

            MessageObject message = (MessageObject) value;

            if (!(message.messageOwner instanceof TLRPC.TL_message)
                    || message.getId() <= 0
                    || message.messageOwner.edit_date <= 0) {
                continue;
            }

            try {
                manager.messageEdited(
                        PluginMessageEditedEvent.capture(account, message)
                );
            } catch (Exception | LinkageError error) {
                FileLog.e("FluxGram message edited event snapshot failed", error);
            }
        }
    }

    private void messagesDeleted(int account, Object... args) {
        if (args.length < 3
                || !(args[0] instanceof ArrayList)
                || !(args[1] instanceof Number)
                || !(args[2] instanceof Number)) {
            return;
        }

        long channelId = ((Number) args[1]).longValue();
        long ownerId = ((Number) args[2]).longValue();

        if (channelId < 0
                || ownerId <= 0
                || UserConfig.getInstance(account).getClientUserId() != ownerId) {
            return;
        }

        PluginManager manager = PluginManager.getInstance();

        if (!manager.hasMessageDeletedPlugins()) {
            return;
        }

        try {
            PluginMessagesDeletedEvent event = PluginMessagesDeletedEvent.capture(
                    account,
                    ownerId,
                    channelId,
                    (ArrayList<?>) args[0]
            );

            if (!event.messageIds.isEmpty()) {
                manager.messagesDeleted(event);
            }
        } catch (Exception | LinkageError error) {
            FileLog.e("FluxGram message deleted event snapshot failed", error);
        }
    }
}
