package org.telegram.messenger.fluxgram;

public final class BottomNavigationSettings {
    public static final int FLOATING = 0;
    public static final int SHOW = 1;
    public static final int HIDE = 2;

    private BottomNavigationSettings() {
    }

    public static int normalize(int mode) {
        if (mode < FLOATING || mode > HIDE) {
            return FLOATING;
        }

        return mode;
    }

    public static boolean isVisible(int mode) {
        return normalize(mode) != HIDE;
    }

    public static boolean followsContentVisibility(int mode) {
        return normalize(mode) == FLOATING;
    }
}
