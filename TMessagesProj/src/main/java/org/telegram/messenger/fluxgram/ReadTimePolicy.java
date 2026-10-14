package org.telegram.messenger.fluxgram;

public final class ReadTimePolicy {
    private ReadTimePolicy() {
    }

    public static boolean shouldCaptureDialog(
            long owner,
            long dialogId,
            boolean userAvailable,
            boolean bot,
            boolean service,
            boolean replyUser,
            boolean anonymous
    ) {
        return owner != 0
                && dialogId > 0
                && dialogId != owner
                && userAvailable
                && !bot
                && !service
                && !replyUser
                && !anonymous;
    }

    public static int readTime(int sentAt, int observedAt) {
        if (sentAt <= 0 || observedAt <= 0) {
            return 0;
        }

        return Math.max(sentAt, observedAt);
    }
}
