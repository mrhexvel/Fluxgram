package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;

import java.util.concurrent.CopyOnWriteArrayList;

public final class PluginUiLifecycle {

    public interface Listener {
        void onBackground();

        void onChatOpened(int account, long dialogId, long topicId);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private PluginUiLifecycle() {
    }

    public static void addListener(Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static void onBackground() {
        for (Listener listener : listeners) {
            try {
                listener.onBackground();
            } catch (Exception | LinkageError error) {
                FileLog.e(error);
            }
        }
    }

    public static void onChatOpened(int account, long dialogId, long topicId) {
        for (Listener listener : listeners) {
            try {
                listener.onChatOpened(account, dialogId, topicId);
            } catch (Exception | LinkageError error) {
                FileLog.e(error);
            }
        }
    }
}
