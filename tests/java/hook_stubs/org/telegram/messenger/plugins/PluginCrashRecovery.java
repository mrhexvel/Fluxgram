package org.telegram.messenger.plugins;

public final class PluginCrashRecovery {
    public static int handled;

    private PluginCrashRecovery() {
    }

    public static void runExternalWorker(String pluginId, Runnable runnable) {
        runnable.run();
    }

    public static void handled(Throwable error) {
        handled++;
    }
}
