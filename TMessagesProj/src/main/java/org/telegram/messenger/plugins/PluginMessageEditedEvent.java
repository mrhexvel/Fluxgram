package org.telegram.messenger.plugins;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.MessageObject;

public final class PluginMessageEditedEvent {
    public final int account;
    public final long ownerId;
    public final int messageId;
    public final long dialogId;
    public final int editDate;
    private final PluginMessageReceivedEvent snapshot;

    private PluginMessageEditedEvent(
            PluginMessageReceivedEvent snapshot,
            int editDate) {
        this.snapshot = snapshot;
        account = snapshot.account;
        ownerId = snapshot.ownerId;
        messageId = snapshot.messageId;
        dialogId = snapshot.dialogId;
        this.editDate = editDate;
    }

    public static PluginMessageEditedEvent capture(int account, MessageObject message) {
        return new PluginMessageEditedEvent(
                PluginMessageReceivedEvent.capture(account, message),
                message.messageOwner.edit_date
        );
    }

    public String toJson() {
        try {
            JSONObject result = snapshot.toJsonObject();
            result.put("edit_date", editDate);

            return result.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not serialize message edited event", error);
        }
    }
}
