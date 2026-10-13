package org.telegram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ConcurrentHashMap;

public final class PluginCrashRecovery {
    static final String PENDING_KEY = "recovery.pending";
    static final String PENDING_PLUGIN_KEY = "recovery.pending_plugin_id";
    static final String LATCHED_KEY = "recovery.latched";
    static final String PLUGIN_KEY = "recovery.plugin_id";
    static final String NOTICE_KEY = "recovery.notice_pending";

    private static final ConcurrentHashMap<Thread, Evidence> pending = new ConcurrentHashMap<>();
    private static volatile SharedPreferences preferences;
    private static boolean installed;

    private static final class Evidence {
        final String pluginId;
        final Throwable error;

        Evidence(String pluginId, Throwable error) {
            this.pluginId = pluginId;
            this.error = error;
        }
    }

    private PluginCrashRecovery() {}

    public static synchronized void install(Context context) {
        if (installed) {
            return;
        }

        preferences = context.getSharedPreferences("fluxgram_plugins", Context.MODE_PRIVATE);

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                Evidence evidence = pending.remove(thread);

                if (evidence != null && evidence.error == error) {
                    SharedPreferences.Editor editor = preferences.edit()
                            .putBoolean(PENDING_KEY, true);

                    if (evidence.pluginId == null) {
                        editor.remove(PENDING_PLUGIN_KEY);
                    } else {
                        editor.putString(PENDING_PLUGIN_KEY, evidence.pluginId);
                    }

                    editor.commit();
                }
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            }
        });

        installed = true;
    }

    static boolean consumePending(SharedPreferences preferences) {
        boolean recovery = preferences.getBoolean(PENDING_KEY, false)
                || preferences.getBoolean(LATCHED_KEY, false);

        if (!preferences.getBoolean(PENDING_KEY, false)) {
            if (!recovery) {
                preferences.edit()
                        .remove(PLUGIN_KEY)
                        .remove(NOTICE_KEY)
                        .commit();
            }

            return recovery;
        }

        String pluginId = preferences.getString(PENDING_PLUGIN_KEY, null);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean("system.enabled", false)
                .putBoolean(LATCHED_KEY, true)
                .putBoolean(NOTICE_KEY, true)
                .remove(PENDING_KEY)
                .remove(PENDING_PLUGIN_KEY);

        if (pluginId == null) {
            editor.remove(PLUGIN_KEY);
        } else {
            editor.putString(PLUGIN_KEY, pluginId);
        }

        editor.commit();

        return true;
    }

    public static void runExternalWorker(String pluginId, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable error) {
            pending.put(Thread.currentThread(), new Evidence(pluginId, error));
            throw error;
        }
    }

    public static void runExternal(String pluginId, Operation operation) throws Exception {
        try {
            operation.run();
        } catch (Exception | Error error) {
            pending.put(Thread.currentThread(), new Evidence(pluginId, error));
            throw error;
        }
    }

    public static void handled(Throwable error) {
        Thread thread = Thread.currentThread();
        Evidence evidence = pending.get(thread);

        if (evidence != null && evidence.error == error) {
            pending.remove(thread, evidence);
        }
    }

    public interface Operation {
        void run() throws Exception;
    }
}
