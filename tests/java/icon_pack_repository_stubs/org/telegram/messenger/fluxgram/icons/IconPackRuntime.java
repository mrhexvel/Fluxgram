package org.telegram.messenger.fluxgram.icons;

import org.telegram.ui.ActionBar.Theme;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class IconPackRuntime {
    public static volatile int refreshCount;
    public static volatile int decodeCount;
    private static volatile long revision;
    private static List<String> publishedActiveIds;
    private static long publishedRequestId;
    private static volatile Runnable demandListener;
    private static volatile boolean pendingDemand;

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
        return rebuild(
                resources,
                store,
                installed,
                activeIds,
                0,
                currentRequest,
                publicationLock);
    }

    public static boolean rebuild(
            Object resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds,
            long requestId,
            BooleanSupplier currentRequest,
            Object publicationLock) {
        synchronized (publicationLock) {
            if (!currentRequest.getAsBoolean() || activeIds.equals(publishedActiveIds)) {
                publishedRequestId = requestId;

                return false;
            }

            publishedActiveIds = new java.util.ArrayList<>(activeIds);
            publishedRequestId = requestId;
            revision++;

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

    static void setDemandListener(Runnable listener) {
        demandListener = listener;
    }

    static boolean hasPendingDemands() {
        return pendingDemand;
    }

    static boolean decodePending(
            BooleanSupplier currentRequest,
            Object publicationLock) {
        return decodePending(0, currentRequest, publicationLock);
    }

    static boolean decodePending(
            long requestId,
            BooleanSupplier currentRequest,
            Object publicationLock) {
        synchronized (publicationLock) {
            if (!currentRequest.getAsBoolean()
                    || requestId != publishedRequestId
                    || !pendingDemand) {
                return false;
            }

            pendingDemand = false;
            decodeCount++;
            revision++;

            return true;
        }
    }

    public static long revision() {
        return revision;
    }

    public static void demand() {
        pendingDemand = true;
        demandListener.run();
    }

    public static void resetPublishedState() {
        publishedActiveIds = null;
        publishedRequestId = 0;
    }

    public static void refreshUi() {
        if (Theme.reloadCount <= refreshCount) {
            throw new AssertionError("Theme resources must reload before runtime UI");
        }

        refreshCount++;
    }
}
