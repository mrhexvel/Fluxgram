package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class ArchiveSettings {
    private static final String REVEAL_ON_PULL = "fluxgram_archive_reveal_on_pull";
    private static final String DISABLE_UNARCHIVE_SWIPE = "fluxgram_disable_unarchive_swipe";

    private static volatile boolean loaded;
    private static volatile boolean revealOnPull;
    private static volatile boolean disableUnarchiveSwipe;

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

    public static boolean disablesUnarchiveSwipe() {
        ensureLoaded();

        return disableUnarchiveSwipe;
    }

    public static synchronized void setDisablesUnarchiveSwipe(boolean disabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(DISABLE_UNARCHIVE_SWIPE, disabled)
                .apply();

        disableUnarchiveSwipe = disabled;
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
            disableUnarchiveSwipe = preferences.getBoolean(DISABLE_UNARCHIVE_SWIPE, false);
            loaded = true;
        }
    }

    private ArchiveSettings() {
    }
}
