package org.telegram.messenger;

public final class AndroidUtilities {
    public static void runOnUIThread(Runnable runnable) {
        runnable.run();
    }
}
