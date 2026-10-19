package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class TransferSettings {
    private static final String INCREASE_DOWNLOAD_SPEED = "fluxgram_increase_download_speed";

    private static volatile boolean loaded;
    private static volatile boolean increaseDownloadSpeed;

    public static boolean increasesDownloadSpeed() {
        ensureLoaded();

        return increaseDownloadSpeed;
    }

    public static synchronized void setIncreasesDownloadSpeed(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(INCREASE_DOWNLOAD_SPEED, enabled)
                .apply();

        increaseDownloadSpeed = enabled;
    }

    public static boolean usesExpandedDownloadWindow(
            boolean hasPreloadPrefix,
            boolean serverExperimentalParams,
            boolean forceSmallChunk
    ) {
        return !forceSmallChunk && (
                hasPreloadPrefix || serverExperimentalParams || increasesDownloadSpeed()
        );
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (TransferSettings.class) {
            if (loaded) {
                return;
            }

            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            increaseDownloadSpeed = preferences.getBoolean(INCREASE_DOWNLOAD_SPEED, false);
            loaded = true;
        }
    }

    private TransferSettings() {
    }
}
