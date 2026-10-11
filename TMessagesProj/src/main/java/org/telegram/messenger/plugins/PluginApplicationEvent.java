package org.telegram.messenger.plugins;

public final class PluginApplicationEvent {
    public final int account;
    public final long ownerId;

    private PluginApplicationEvent(int account, long ownerId) {
        this.account = account;
        this.ownerId = ownerId;
    }

    public static PluginApplicationEvent capture(int account, long ownerId) {
        if (account < 0 || ownerId <= 0) {
            throw new IllegalArgumentException("Invalid application event identity");
        }

        return new PluginApplicationEvent(account, ownerId);
    }
}
