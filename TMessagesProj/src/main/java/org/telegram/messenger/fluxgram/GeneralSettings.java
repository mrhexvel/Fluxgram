package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.text.NumberFormat;
import java.util.Locale;

public final class GeneralSettings {
    private static final String FULL_NUMBER_FORMAT = "fluxgram_full_number_format";

    private static volatile boolean loaded;
    private static volatile boolean fullNumberFormat;

    public static boolean usesFullNumberFormat() {
        ensureLoaded();

        return fullNumberFormat;
    }

    public static synchronized void setUsesFullNumberFormat(boolean enabled) {
        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(FULL_NUMBER_FORMAT, enabled)
                .apply();

        fullNumberFormat = enabled;
        loaded = true;
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
            loaded = true;
        }
    }

    private GeneralSettings() {
    }
}
