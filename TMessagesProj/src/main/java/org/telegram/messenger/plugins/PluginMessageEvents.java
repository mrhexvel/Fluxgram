package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
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
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didReceiveNewMessages
                || args.length < 4
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
}
