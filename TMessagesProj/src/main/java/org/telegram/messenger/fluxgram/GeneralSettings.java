package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.text.NumberFormat;
import java.util.Locale;

public final class GeneralSettings {
    private static final String FULL_NUMBER_FORMAT = "fluxgram_full_number_format";
    private static final String SHOW_TIME_SECONDS = "fluxgram_show_time_seconds";
    private static final String RELATIVE_LAST_SEEN = "fluxgram_relative_last_seen";

    private static volatile boolean loaded;
    private static volatile boolean fullNumberFormat;
    private static volatile boolean showTimeSeconds;
    private static volatile boolean relativeLastSeen;

    public static boolean usesFullNumberFormat() {
        ensureLoaded();

        return fullNumberFormat;
    }

    public static synchronized void setUsesFullNumberFormat(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(FULL_NUMBER_FORMAT, enabled)
                .apply();

        fullNumberFormat = enabled;
    }

    public static boolean showsTimeSeconds() {
        ensureLoaded();

        return showTimeSeconds;
    }

    public static synchronized void setShowsTimeSeconds(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_TIME_SECONDS, enabled)
                .apply();

        showTimeSeconds = enabled;
    }

    public static boolean usesRelativeLastSeen() {
        ensureLoaded();

        return relativeLastSeen;
    }

    public static synchronized void setUsesRelativeLastSeen(boolean enabled) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(RELATIVE_LAST_SEEN, enabled)
                .apply();

        relativeLastSeen = enabled;
    }

    public static String formatFullNumber(int number, int[] rounded, Locale locale) {
        if (rounded != null) {
            rounded[0] = number;
        }

        Locale resolvedLocale = locale != null ? locale : Locale.getDefault();
        NumberFormat formatter = NumberFormat.getIntegerInstance(resolvedLocale);
        formatter.setGroupingUsed(true);

        return formatter.format(number);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (GeneralSettings.class) {
            if (loaded) {
                return;
            }

            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            fullNumberFormat = preferences.getBoolean(FULL_NUMBER_FORMAT, false);
            showTimeSeconds = preferences.getBoolean(SHOW_TIME_SECONDS, false);
            relativeLastSeen = preferences.getBoolean(RELATIVE_LAST_SEEN, false);
            loaded = true;
        }
    }

    private GeneralSettings() {
    }
}
