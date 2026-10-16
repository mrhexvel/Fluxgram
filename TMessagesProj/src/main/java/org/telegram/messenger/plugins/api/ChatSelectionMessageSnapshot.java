package org.telegram.messenger.plugins.api;

public final class ChatSelectionMessageSnapshot {
    public final int messageId;
    public final long dialogId;
    public final Long senderId;

    public ChatSelectionMessageSnapshot(int messageId, long dialogId, Long senderId) {
        this.messageId = messageId;
        this.dialogId = dialogId;
        this.senderId = senderId;
    }
}
