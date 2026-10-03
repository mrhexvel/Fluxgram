package org.telegram.messenger.plugins;

/** Read-only snapshot of one new outgoing text part, before local storage and dispatch.
 * This event does not confirm delivery. Retries, forwards, inline results and media are excluded.
 */
public final class BeforeSendEvent {
    public final int account;
    public final long dialogId;
    public final String text;
    public final int scheduleDate;

    public BeforeSendEvent(int account, long dialogId, String text, int scheduleDate) {
        this.account = account;
        this.dialogId = dialogId;
        this.text = text;
        this.scheduleDate = scheduleDate;
    }
}
