package org.telegram.messenger.fluxgram;

public final class NavigationSettings {
    public static final int TRANSITION_DEFAULT = 0;
    public static final int TRANSITION_AOSP = 1;
    public static final int TRANSITION_SPRING = 2;

    private NavigationSettings() {
    }

    public static int normalizeTransitionStyle(int style) {
        if (style < TRANSITION_DEFAULT || style > TRANSITION_SPRING) {
            return TRANSITION_DEFAULT;
        }

        return style;
    }

}
