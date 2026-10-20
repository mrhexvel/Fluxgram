package org.telegram.ui;

import android.view.View;

import org.telegram.messenger.fluxgram.RootNavigationSnapshot;

public interface MainTabsActivityController {
    void setTabsVisible(boolean visible);

    boolean openAccountSelector(View anchor);

    RootNavigationSnapshot getRootNavigationSnapshot();

    boolean navigateToRootDestination(
            RootNavigationSnapshot snapshot,
            String destination
    );
}
