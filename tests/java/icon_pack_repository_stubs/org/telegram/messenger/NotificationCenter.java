package org.telegram.messenger;

import org.telegram.messenger.fluxgram.icons.IconPackRuntime;
import org.telegram.ui.ActionBar.Theme;

public final class NotificationCenter {
    public static final int reloadInterface = 1;
    public static final int fluxgramIconPackChanged = 2;
    public static volatile int iconPackChangedCount;

    private static final NotificationCenter INSTANCE = new NotificationCenter();

    public static NotificationCenter getGlobalInstance() {
        return INSTANCE;
    }

    public void postNotificationName(int id, Object... args) {
        if (id == fluxgramIconPackChanged) {
            if (Theme.reloadCount <= iconPackChangedCount
                    || IconPackRuntime.refreshCount <= iconPackChangedCount) {
                throw new AssertionError("Icon pack refresh order is invalid");
            }

            iconPackChangedCount++;
        }
    }

    public static void resetIconPackChanges() {
        iconPackChangedCount = 0;
        Theme.reloadCount = 0;
        IconPackRuntime.refreshCount = 0;
        IconPackRuntime.decodeCount = 0;
    }
}
