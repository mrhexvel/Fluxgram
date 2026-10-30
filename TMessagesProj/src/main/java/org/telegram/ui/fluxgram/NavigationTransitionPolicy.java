package org.telegram.ui.fluxgram;

import org.telegram.messenger.fluxgram.NavigationSettings;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.INavigationLayout;

public final class NavigationTransitionPolicy {
    private NavigationTransitionPolicy() {
    }

    public static void apply(INavigationLayout layout, int style) {
        if (!(layout instanceof ActionBarLayout)) {
            return;
        }

        ActionBarLayout actionBarLayout = (ActionBarLayout) layout;
        int normalizedStyle = NavigationSettings.normalizeTransitionStyle(style);

        if (normalizedStyle == NavigationSettings.TRANSITION_SPRING) {
            actionBarLayout.setFallbackTransitionAnimation(
                    ActionBarLayout.FALLBACK_TRANSITION_SPRING,
                    null,
                    150,
                    1.0f
            );
        } else {
            actionBarLayout.setFallbackTransitionAnimation(
                    ActionBarLayout.FALLBACK_TRANSITION_STANDARD,
                    null,
                    150,
                    1.0f
            );
        }
    }
}
