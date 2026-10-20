package org.telegram.ui.fluxgram;

import android.animation.TimeInterpolator;

import org.telegram.messenger.fluxgram.NavigationSettings;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;

public final class NavigationTransitionPolicy {
    private static final TimeInterpolator SPRING_INTERPOLATOR = input -> {
        double response = 1.0 - Math.exp(-7.0 * input) * (1.0 + 7.0 * input);
        double end = 1.0 - Math.exp(-7.0) * 8.0;

        return (float) Math.max(0.0, Math.min(1.0, response / end));
    };

    private NavigationTransitionPolicy() {
    }

    public static void apply(INavigationLayout layout, int style) {
        if (!(layout instanceof ActionBarLayout)) {
            return;
        }

        ActionBarLayout actionBarLayout = (ActionBarLayout) layout;
        int normalizedStyle = NavigationSettings.normalizeTransitionStyle(style);

        if (normalizedStyle == NavigationSettings.TRANSITION_AOSP) {
            actionBarLayout.setFallbackTransitionAnimation(
                    CubicBezierInterpolator.EmphasizedDecelerate,
                    220,
                    1.0f
            );
        } else if (normalizedStyle == NavigationSettings.TRANSITION_SPRING) {
            actionBarLayout.setFallbackTransitionAnimation(
                    SPRING_INTERPOLATOR,
                    260,
                    1.15f
            );
        } else {
            actionBarLayout.setFallbackTransitionAnimation(null, 0, 1.0f);
        }
    }
}
