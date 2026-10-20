package org.telegram.messenger.fluxgram;

public final class TabletModeSettings {
    public static final int AUTO = 0;
    public static final int ON = 1;
    public static final int OFF = 2;

    private TabletModeSettings() {
    }

    public static int normalize(int mode) {
        if (mode < AUTO || mode > OFF) {
            return AUTO;
        }

        return mode;
    }

    public static boolean resolve(int mode, boolean automaticValue) {
        if (mode == ON) {
            return true;
        }
        if (mode == OFF) {
            return false;
        }

        return automaticValue;
    }
}
