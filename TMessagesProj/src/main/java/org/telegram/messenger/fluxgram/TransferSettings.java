package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class TransferSettings {
    private static final String INCREASE_DOWNLOAD_SPEED = "fluxgram_increase_download_speed";
    private static final String INCREASE_UPLOAD_SPEED = "fluxgram_increase_upload_speed";

    private static volatile boolean loaded;
    private static volatile boolean increaseDownloadSpeed;
    private static volatile boolean increaseUploadSpeed;

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

    public static boolean increasesUploadSpeed() {
        ensureLoaded();

        return increaseUploadSpeed;
    }

    public static synchronized void setIncreasesUploadSpeed(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(INCREASE_UPLOAD_SPEED, enabled)
                .apply();

        increaseUploadSpeed = enabled;
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

    public static int resolveMinimumUploadChunkSize(
            boolean slowNetwork,
            int defaultSize,
            int windowSize,
            int initialRequests
    ) {
        if (slowNetwork || !increasesUploadSpeed()) {
            return defaultSize;
        }

        return Math.max(defaultSize, windowSize / initialRequests);
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
            increaseUploadSpeed = preferences.getBoolean(INCREASE_UPLOAD_SPEED, false);
            loaded = true;
        }
    }

    private TransferSettings() {
    }
}
