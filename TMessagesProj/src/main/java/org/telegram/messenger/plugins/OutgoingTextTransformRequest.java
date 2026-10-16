package org.telegram.messenger.plugins;

public final class OutgoingTextTransformRequest {
    public static final int MAX_TEXT_LENGTH = 32768;

    public final long requestId;
    public final int account;
    public final long ownerId;
    public final long dialogId;
    public final long topicId;
    public final String text;
    public final boolean notify;
    public final int scheduleDate;
    public final int scheduleRepeatPeriod;
    public final long payStars;

    public OutgoingTextTransformRequest(
            long requestId,
            int account,
            long ownerId,
            long dialogId,
            long topicId,
            String text,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            long payStars) {
        this.requestId = requestId;
        this.account = account;
        this.ownerId = ownerId;
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.text = text;
        this.notify = notify;
        this.scheduleDate = scheduleDate;
        this.scheduleRepeatPeriod = scheduleRepeatPeriod;
        this.payStars = payStars;
    }

    OutgoingTextTransformRequest withText(String transformedText) {
        return new OutgoingTextTransformRequest(
                requestId,
                account,
                ownerId,
                dialogId,
                topicId,
                transformedText,
                notify,
                scheduleDate,
                scheduleRepeatPeriod,
                payStars
        );
    }

    public boolean isValid() {
        return requestId > 0
                && account >= 0
                && ownerId > 0
                && dialogId != 0
                && topicId >= 0
                && text != null
                && text.length() <= MAX_TEXT_LENGTH
                && scheduleDate >= 0
                && scheduleRepeatPeriod >= 0
                && payStars >= 0;
    }
}
