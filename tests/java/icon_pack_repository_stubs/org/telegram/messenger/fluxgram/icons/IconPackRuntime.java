package org.telegram.messenger.fluxgram.icons;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class IconPackRuntime {
    public static boolean rebuild(
            Object resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds) {
        return true;
    }

    public static boolean rebuild(
            Object resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds,
            BooleanSupplier currentRequest) {
        return currentRequest.getAsBoolean();
    }

    public static boolean rebuild(
            Object resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds,
            BooleanSupplier currentRequest,
            Object publicationLock) {
        synchronized (publicationLock) {
            return currentRequest.getAsBoolean();
        }
    }

    public static boolean clear() {
        return true;
    }

    public static boolean clearIfCurrent(
            BooleanSupplier currentRequest,
            Object publicationLock) {
        synchronized (publicationLock) {
            return currentRequest.getAsBoolean();
        }
    }

    public static void refreshUi() {
    }
}
