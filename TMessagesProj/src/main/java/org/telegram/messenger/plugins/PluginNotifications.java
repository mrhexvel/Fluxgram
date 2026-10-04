package org.telegram.messenger.plugins;

import android.os.Looper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

import java.util.concurrent.CopyOnWriteArrayList;

/** Foreground-only arbitration before a message enters the system notification queue. */
public final class PluginNotifications {
    public interface Listener {
        boolean isActive();
        /** True only after the plugin successfully displayed this message. */
        boolean onMessage(int account, MessageObject message);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private PluginNotifications() { }

    public static void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static boolean isForeground() {
        return !ApplicationLoader.mainInterfacePaused
                && ApplicationLoader.isScreenOn
                && !SharedConfig.appLocked
                && !SharedConfig.isWaitingForPasscodeEnter;
    }

    public static boolean canOffer() {
        return !listeners.isEmpty() && isForeground();
    }

    public static boolean isEligible(MessageObject message) {
        return message != null && message.messageOwner != null
                && message.getId() != 0 && !message.isOut() && !message.scheduled && !message.localEdit
                && !message.isStoryPush && !message.isStoryReactionPush
                && !message.isReactionPush && !message.isOauthPush
                && (message.messageOwner.action == null
                    || message.messageOwner.action instanceof TLRPC.TL_messageActionEmpty);
    }

    public static boolean offer(int account, MessageObject message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Notification plugins must run on the UI thread");
        }
        if (!canOffer() || !isEligible(message)) return false;
        for (Listener listener : listeners) {
            try {
                if (listener.isActive() && listener.onMessage(account, message)
                        && listener.isActive() && isForeground()) {
                    return true;
                }
            } catch (Exception | LinkageError error) {
                // Plugin failures must never swallow the ordinary notification.
                FileLog.e(error);
            }
        }
        return false;
    }
}
