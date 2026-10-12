package org.telegram.messenger.plugins;

import org.telegram.messenger.R;

public final class BuiltInPlugins {
    private static final Plugin IN_APP_NOTIFICATIONS =
            new PythonPluginAdapter(
                    "fluxgram.in_app_notifications",
                    "fluxgram_notifications",
                    R.string.FluxGramInAppNotifications,
                    R.string.FluxGramInAppNotificationsInfo,
                    R.drawable.msg_notifications,
                    "1.0.0");

    private BuiltInPlugins() {}

    public static void register() {
        PluginManager.getInstance().register(IN_APP_NOTIFICATIONS);
    }
}
