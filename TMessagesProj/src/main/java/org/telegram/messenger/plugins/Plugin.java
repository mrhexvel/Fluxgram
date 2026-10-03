package org.telegram.messenger.plugins;

/** A built-in plugin. Callbacks run synchronously and must not block or send messages. */
public interface Plugin {
    /** Stable, non-empty identifier, unique within the application. */
    String getId();

    void beforeSend(BeforeSendEvent event);
}
