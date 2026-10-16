package org.telegram.messenger.plugins;

import java.io.File;

public interface Plugin {
    String getId();

    String getName();

    String getDescription();

    String getVersion();

    default File getIconFile() {
        return null;
    }

    default int getIconResource() {
        return 0;
    }

    default void onEnabledChanged(boolean enabled) {}

    default void messageReceived(PluginMessageReceivedEvent event) {}

    default boolean hasMessageReceivedSubscribers() {
        return false;
    }

    default void messageEdited(PluginMessageEditedEvent event) {}

    default boolean hasMessageEditedSubscribers() {
        return false;
    }

    default void messagesDeleted(PluginMessagesDeletedEvent event) {}

    default boolean hasMessageDeletedSubscribers() {
        return false;
    }

    default void chatOpened(PluginChatEvent event) {}

    default boolean hasChatOpenedSubscribers() {
        return false;
    }

    default void chatClosed(PluginChatEvent event) {}

    default boolean hasChatClosedSubscribers() {
        return false;
    }

    default void applicationForegrounded(PluginApplicationEvent event) {}

    default boolean hasApplicationForegroundedSubscribers() {
        return false;
    }

    default void applicationBackgrounded(PluginApplicationEvent event) {}

    default boolean hasApplicationBackgroundedSubscribers() {
        return false;
    }

    default boolean hasOutgoingTextTransformer() {
        return false;
    }

    default void transformOutgoingText(
            OutgoingTextTransformRequest request,
            OutgoingTextTransformCallback callback) {
        callback.complete(null);
    }

    void beforeSend(BeforeSendEvent event);
}
