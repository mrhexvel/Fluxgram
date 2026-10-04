package org.telegram.messenger.plugins;

import org.telegram.messenger.R;

/** Registers the built-in plugins during application initialization. */
public final class BuiltInPlugins {
    private static final Plugin IN_APP_NOTIFICATIONS = new PythonPluginAdapter(
            "fluxgram.in_app_notifications",
            "fluxgram_notifications",
            R.string.FluxGramInAppNotifications,
            R.string.FluxGramInAppNotificationsInfo,
            "1.0.0"
    );

    private BuiltInPlugins() {
    }

    public static void register() {
        // Reuse the instance so repeated initialization cannot duplicate callbacks.
        PluginManager.getInstance().register(IN_APP_NOTIFICATIONS);
    }
}
