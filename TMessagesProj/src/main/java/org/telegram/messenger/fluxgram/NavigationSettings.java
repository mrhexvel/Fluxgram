package org.telegram.messenger.fluxgram;

public final class NavigationSettings {
    public static final int TRANSITION_DEFAULT = 0;
    public static final int TRANSITION_AOSP = 1;
    public static final int TRANSITION_SPRING = 2;

    public static final int MIN_PREDICTIVE_BACK_INTENSITY = 25;
    public static final int MAX_PREDICTIVE_BACK_INTENSITY = 100;
    public static final int DEFAULT_PREDICTIVE_BACK_INTENSITY = 100;

    private NavigationSettings() {
    }

    public static int normalizeTransitionStyle(int style) {
        if (style < TRANSITION_DEFAULT || style > TRANSITION_SPRING) {
            return TRANSITION_DEFAULT;
        }

        return style;
    }

    public static int normalizePredictiveBackIntensity(int intensity) {
        return Math.max(
                MIN_PREDICTIVE_BACK_INTENSITY,
                Math.min(MAX_PREDICTIVE_BACK_INTENSITY, intensity)
        );
    }

    public static float scalePredictiveBackProgress(float progress, int intensity) {
        float normalizedProgress = Math.max(0.0f, Math.min(1.0f, progress));
        float scale = normalizePredictiveBackIntensity(intensity) / 100.0f;

        return normalizedProgress * scale;
    }
}
