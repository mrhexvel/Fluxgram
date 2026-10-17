package org.telegram.messenger.plugins;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;

import java.util.concurrent.CopyOnWriteArrayList;

public final class PluginDeveloperBridge {
    public static final long SESSION_DURATION_MS = 15 * 60 * 1000L;

    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static long expiresAt;

    private static final Runnable expireSession = () -> {
        boolean changed = false;

        synchronized (PluginDeveloperBridge.class) {
            if (expiresAt > 0 && SystemClock.elapsedRealtime() >= expiresAt) {
                expiresAt = 0;
                changed = true;
            }
        }

        if (changed) {
            notifyChanged();
        }
    };

    private PluginDeveloperBridge() {}

    public static boolean isAvailable() {
        return BuildConfig.DEBUG
                || PluginManager.getInstance().isSystemEnabled() && isSessionActive();
    }

    public static synchronized boolean isSessionActive() {
        if (expiresAt == 0) {
            return false;
        }

        if (SystemClock.elapsedRealtime() >= expiresAt) {
            expiresAt = 0;
            return false;
        }

        return true;
    }

    public static void startSession() {
        synchronized (PluginDeveloperBridge.class) {
            expiresAt = SystemClock.elapsedRealtime() + SESSION_DURATION_MS;
        }

        AndroidUtilities.cancelRunOnUIThread(expireSession);
        AndroidUtilities.runOnUIThread(expireSession, SESSION_DURATION_MS);
        notifyChanged();
    }

    public static void stopSession() {
        boolean changed;

        synchronized (PluginDeveloperBridge.class) {
            changed = expiresAt != 0;
            expiresAt = 0;
        }

        AndroidUtilities.cancelRunOnUIThread(expireSession);

        if (changed) {
            notifyChanged();
        }
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }
}
