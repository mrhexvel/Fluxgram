package org.telegram.messenger.fluxgram;

import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;

import java.util.function.Consumer;

public final class LocalStorageCleanup {
    private LocalStorageCleanup() {
    }

    public static void clear(int account, Consumer<Boolean> completed) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        Collector collector = new Collector(account, owner, completed);

        EditHistory.clearVersions(account, collector::complete);
        MessagesStorage.getInstance(account).getDeletedArchive().clear(collector::complete);
        SavedAttachments.get(account).clearFiles(collector::complete);
    }

    private static final class Collector {
        private final int account;
        private final long owner;
        private final Consumer<Boolean> completed;
        private int responses;
        private boolean success = true;

        private Collector(int account, long owner, Consumer<Boolean> completed) {
            this.account = account;
            this.owner = owner;
            this.completed = completed;
        }

        private synchronized void complete(boolean value) {
            success &= value;
            responses++;

            if (responses == 3 && owner == UserConfig.getInstance(account).getClientUserId()) {
                completed.accept(success);
            }
        }
    }
}
