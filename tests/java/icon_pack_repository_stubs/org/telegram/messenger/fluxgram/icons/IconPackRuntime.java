package org.telegram.messenger.fluxgram.icons;

import org.telegram.ui.ActionBar.Theme;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class IconPackRuntime {
    public static volatile int refreshCount;
    private static List<String> publishedActiveIds;

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
            if (!currentRequest.getAsBoolean() || activeIds.equals(publishedActiveIds)) {
                return false;
            }

            publishedActiveIds = new java.util.ArrayList<>(activeIds);

            return true;
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
        if (Theme.reloadCount <= refreshCount) {
            throw new AssertionError("Theme resources must reload before runtime UI");
        }

        refreshCount++;
    }
}
