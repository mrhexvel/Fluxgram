package org.telegram.messenger.plugins;

/** A built-in plugin. Callbacks run synchronously and must not block or send messages. */
public interface Plugin {
    String getId();

    String getName();

    String getDescription();

    String getVersion();

    default void onEnabledChanged(boolean enabled) {
    }

    void beforeSend(BeforeSendEvent event);
}