package org.telegram.messenger.fluxgram;

public final class NavigationSettings {
    public static final int TRANSITION_DEFAULT = 0;
    public static final int TRANSITION_SPRING = 2;

    private NavigationSettings() {
    }

    public static int normalizeTransitionStyle(int style) {
        return style == TRANSITION_SPRING ? TRANSITION_SPRING : TRANSITION_DEFAULT;
    }

}
