package org.telegram.messenger.fluxgram.icons;

import java.util.List;

public final class IconPackRuntime {
    public static boolean rebuild(
            Object resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds) {
        return true;
    }

    public static boolean clear() {
        return true;
    }
}
