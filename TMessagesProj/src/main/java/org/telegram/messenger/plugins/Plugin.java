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

    void beforeSend(BeforeSendEvent event);
}
