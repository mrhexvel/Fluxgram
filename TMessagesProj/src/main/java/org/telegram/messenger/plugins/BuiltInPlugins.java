package org.telegram.messenger.plugins;

/** Registers the built-in plugins during application initialization. */
public final class BuiltInPlugins {
    private static final Plugin LOGGING = new LoggingPlugin();

    private BuiltInPlugins() {
    }

    public static void register() {
        // Reuse the instance so repeated initialization cannot duplicate callbacks.
        PluginManager.getInstance().register(LOGGING);
    }
}
