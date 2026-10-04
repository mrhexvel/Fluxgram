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

    void beforeSend(BeforeSendEvent event);
}
