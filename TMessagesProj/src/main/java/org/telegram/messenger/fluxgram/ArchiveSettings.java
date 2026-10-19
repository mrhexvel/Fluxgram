package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class ArchiveSettings {
    private static final String REVEAL_ON_PULL = "fluxgram_archive_reveal_on_pull";

    private static volatile boolean loaded;
    private static volatile boolean revealOnPull;

    public static boolean revealsOnPull() {
        ensureLoaded();

        return revealOnPull;
    }

    public static synchronized void setRevealsOnPull(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(REVEAL_ON_PULL, enabled)
                .apply();

        revealOnPull = enabled;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (ArchiveSettings.class) {
            if (loaded) {
                return;
            }

            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            revealOnPull = preferences.getBoolean(REVEAL_ON_PULL, true);
            loaded = true;
        }
    }

    private ArchiveSettings() {
    }
}
