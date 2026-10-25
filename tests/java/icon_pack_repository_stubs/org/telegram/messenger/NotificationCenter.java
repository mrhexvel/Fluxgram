package org.telegram.messenger;

public final class NotificationCenter {
    public static final int reloadInterface = 1;

    private static final NotificationCenter INSTANCE = new NotificationCenter();

    public static NotificationCenter getGlobalInstance() {
        return INSTANCE;
    }

    public void postNotificationName(int id, Object... args) {
    }
}
