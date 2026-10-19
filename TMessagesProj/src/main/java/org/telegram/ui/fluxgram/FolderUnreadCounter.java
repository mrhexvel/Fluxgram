package org.telegram.ui.fluxgram;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.fluxgram.FolderSettings;

public final class FolderUnreadCounter {
    public static int resolve(
            int account,
            MessagesController.DialogFilter filter,
            FolderSettings.Policy policy
    ) {
        if (policy.getCounterMode() == FolderSettings.COUNTER_HIDDEN) {
            return 0;
        }

        if (filter == null || filter.isDefault()) {
            MessagesStorage storage = MessagesStorage.getInstance(account);
            if (policy.getCounterMode() == FolderSettings.COUNTER_UNMUTED) {
                return storage.getMainUnmutedUnreadCount();
            }

            return storage.getMainAllUnreadCount();
        }

        if (policy.getCounterMode() == FolderSettings.COUNTER_UNMUTED) {
            return filter.unmutedUnreadCount;
        }

        return filter.unreadCount;
    }

    private FolderUnreadCounter() {
    }
}
