package org.telegram.ui.ActionBar;

public final class Theme {
    public static volatile int reloadCount;

    public static void reloadAllResources(Object context) {
        reloadCount++;
    }
}
