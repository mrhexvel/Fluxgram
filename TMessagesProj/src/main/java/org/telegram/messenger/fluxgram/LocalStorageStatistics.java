package org.telegram.messenger.fluxgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;

import java.util.function.Consumer;

public final class LocalStorageStatistics {
    public static final class Result {
        public final long editVersions;
        public final long deletedMessages;
        public final long attachments;
        public final long attachmentBytes;
        public final long attachmentFailures;
        public final boolean editVersionsAvailable;
        public final boolean deletedMessagesAvailable;
        public final boolean attachmentsAvailable;

        private Result(Collector collector) {
            editVersions = collector.editVersions;
            deletedMessages = collector.deletedMessages;
            attachments = collector.attachments;
            attachmentBytes = collector.attachmentBytes;
            attachmentFailures = collector.attachmentFailures;
            editVersionsAvailable = collector.editVersionsAvailable;
            deletedMessagesAvailable = collector.deletedMessagesAvailable;
            attachmentsAvailable = collector.attachmentsAvailable;
        }
    }

    private static final class Collector {
        private final int account;
        private final long owner;
        private final Consumer<Result> completed;
        private int responses;
        private long editVersions;
        private long deletedMessages;
        private long attachments;
        private long attachmentBytes;
        private long attachmentFailures;
        private boolean editVersionsAvailable;
        private boolean deletedMessagesAvailable;
        private boolean attachmentsAvailable;

        private Collector(int account, long owner, Consumer<Result> completed) {
            this.account = account;
            this.owner = owner;
            this.completed = completed;
        }

        private void setStorage(long editVersions, boolean editVersionsAvailable,
                long deletedMessages, boolean deletedMessagesAvailable) {
            this.editVersions = editVersions;
            this.editVersionsAvailable = editVersionsAvailable;
            this.deletedMessages = deletedMessages;
            this.deletedMessagesAvailable = deletedMessagesAvailable;
            responses++;
            finish();
        }

        private void setAttachments(SavedAttachments.Statistics statistics) {
            if (statistics != null) {
                attachments = statistics.count;
                attachmentBytes = statistics.bytes;
                attachmentFailures = statistics.failures;
                attachmentsAvailable = true;
            }

            responses++;
            finish();
        }

        private void finish() {
            if (responses != 2) {
                return;
            }

            completed.accept(owner == UserConfig.getInstance(account).getClientUserId()
                    ? new Result(this) : null);
        }
    }

    private LocalStorageStatistics() {
    }

    public static void load(int account, Consumer<Result> completed) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        Collector collector = new Collector(account, owner, completed);
        MessagesStorage storage = MessagesStorage.getInstance(account);

        storage.getStorageQueue().postRunnable(() -> {
            long editVersions = 0;
            boolean editVersionsAvailable = false;

            try {
                editVersions = EditHistory.countVersionsOnStorageQueue(account, owner);
                editVersionsAvailable = true;
            } catch (Exception error) {
                FileLog.e(error);
            }

            long deletedMessages = 0;
            boolean deletedMessagesAvailable = false;

            try {
                deletedMessages = storage.getDeletedArchive().countOnStorageQueue();
                deletedMessagesAvailable = true;
            } catch (Exception error) {
                FileLog.e(error);
            }

            long editVersionsResult = editVersions;
            boolean editVersionsResultAvailable = editVersionsAvailable;
            long deletedMessagesResult = deletedMessages;
            boolean deletedMessagesResultAvailable = deletedMessagesAvailable;
            AndroidUtilities.runOnUIThread(() -> collector.setStorage(
                    editVersionsResult,
                    editVersionsResultAvailable,
                    deletedMessagesResult,
                    deletedMessagesResultAvailable
            ));
        });

        SavedAttachments.get(account).loadStatistics(collector::setAttachments);
    }
}
