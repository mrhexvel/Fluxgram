package org.telegram.messenger.plugins;

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
