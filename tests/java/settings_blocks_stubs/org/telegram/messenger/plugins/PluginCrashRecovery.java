package org.telegram.messenger.plugins;

public final class PluginCrashRecovery {
    public interface Operation {
        void run() throws Exception;
    }

    public static void runExternal(String pluginId, Operation operation) throws Exception {
        operation.run();
    }

    public static void handled(Throwable error) {
    }
}
