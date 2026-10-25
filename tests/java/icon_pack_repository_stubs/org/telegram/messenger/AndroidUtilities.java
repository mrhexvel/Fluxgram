package org.telegram.messenger;

import java.util.ArrayList;
import java.util.List;

public final class AndroidUtilities {
    private static final List<Runnable> pending = new ArrayList<>();
    private static boolean deferred;

    public static void runOnUIThread(Runnable runnable) {
        synchronized (pending) {
            if (deferred) {
                pending.add(runnable);

                return;
            }
        }

        runnable.run();
    }

    public static void setUiDeferred(boolean value) {
        synchronized (pending) {
            deferred = value;
        }
    }

    public static int pendingUiCount() {
        synchronized (pending) {
            return pending.size();
        }
    }

    public static void drainUi() {
        List<Runnable> callbacks;

        synchronized (pending) {
            callbacks = new ArrayList<>(pending);
            pending.clear();
        }

        for (Runnable callback : callbacks) {
            callback.run();
        }
    }
}
