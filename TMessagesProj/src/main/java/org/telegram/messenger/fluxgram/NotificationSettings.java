package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public final class NotificationSettings {
    private static final String IN_APP_VIBRATION = "EnableInAppVibrate";
    private static final String DISABLE_NOTIFICATION_DELAY = "fluxgram_disable_notification_delay";

    private NotificationSettings() {
    }

    public static boolean isInAppVibrationEnabled(int account) {
        return preferences(account).getBoolean(IN_APP_VIBRATION, true);
    }

    public static void setInAppVibrationEnabled(int account, boolean enabled) {
        preferences(account).edit().putBoolean(IN_APP_VIBRATION, enabled).apply();
    }

    public static boolean isNotificationDelayDisabled(int account) {
        return preferences(account).getBoolean(DISABLE_NOTIFICATION_DELAY, false);
    }

    public static void setNotificationDelayDisabled(int account, boolean disabled) {
        preferences(account).edit().putBoolean(DISABLE_NOTIFICATION_DELAY, disabled).apply();
    }

    private static SharedPreferences preferences(int account) {
        return MessagesController.getNotificationsSettings(account);
    }
}
