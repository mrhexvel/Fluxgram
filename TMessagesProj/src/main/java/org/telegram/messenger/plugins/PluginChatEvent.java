package org.telegram.messenger.plugins;

public final class PluginChatEvent {
    public final int account;
    public final long ownerId;
    public final long presentationId;
    public final long dialogId;
    public final long topicId;

    private PluginChatEvent(
            int account,
            long ownerId,
            long presentationId,
            long dialogId,
            long topicId) {
        this.account = account;
        this.ownerId = ownerId;
        this.presentationId = presentationId;
        this.dialogId = dialogId;
        this.topicId = topicId;
    }

    public static PluginChatEvent capture(
            int account,
            long ownerId,
            long presentationId,
            long dialogId,
            long topicId) {
        if (account < 0
                || ownerId <= 0
                || presentationId <= 0
                || dialogId == 0
                || topicId < 0) {
            throw new IllegalArgumentException("Invalid chat event identity");
        }

        return new PluginChatEvent(
                account,
                ownerId,
                presentationId,
                dialogId,
                topicId
        );
    }
}
