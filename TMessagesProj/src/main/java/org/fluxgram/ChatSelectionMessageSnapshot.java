package org.fluxgram;

/**
 * Immutable identity snapshot for one server-backed message selected in an ordinary cloud chat.
 *
 * <p>The snapshot deliberately contains no Telegram model, text, or live UI reference. The host
 * matches {@link #messageId} and {@link #dialogId} again before invoking an action. Multi-selection,
 * local pending messages, secret chats, scheduled chats, previews, report flows, and special chat
 * modes do not produce this action surface.</p>
 */
public final class ChatSelectionMessageSnapshot {
    /** Positive server message ID within {@link #dialogId}. */
    public final int messageId;

    /** Telegram dialog ID containing the selected message. */
    public final long dialogId;

    /** Sender peer ID, or {@code null} when Telegram does not expose one for this message. */
    public final Long senderId;

    /**
     * Creates an immutable selected-message identity.
     *
     * @param messageId positive server message ID
     * @param dialogId non-zero Telegram dialog ID
     * @param senderId sender peer ID, or {@code null} when unavailable
     */
    public ChatSelectionMessageSnapshot(int messageId, long dialogId, Long senderId) {
        this.messageId = messageId;
        this.dialogId = dialogId;
        this.senderId = senderId;
    }
}
