package org.telegram.messenger.plugins;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PluginMessagesDeletedEvent {
    public final int account;
    public final long ownerId;
    public final Long channelId;
    public final List<Integer> messageIds;

    private PluginMessagesDeletedEvent(
            int account,
            long ownerId,
            Long channelId,
            List<Integer> messageIds) {
        this.account = account;
        this.ownerId = ownerId;
        this.channelId = channelId;
        this.messageIds = messageIds;
    }

    public static PluginMessagesDeletedEvent capture(
            int account,
            long ownerId,
            long channelId,
            ArrayList<?> values) {
        ArrayList<Integer> messageIds = new ArrayList<>(values.size());

        for (Object value : values) {
            if (value instanceof Integer && (Integer) value > 0) {
                messageIds.add((Integer) value);
            }
        }

        return new PluginMessagesDeletedEvent(
                account,
                ownerId,
                channelId == 0 ? null : channelId,
                Collections.unmodifiableList(messageIds)
        );
    }

    public String toJson() {
        try {
            JSONArray ids = new JSONArray();

            for (int messageId : messageIds) {
                ids.put(messageId);
            }

            JSONObject result = new JSONObject();
            result.put("channel_id", channelId == null ? JSONObject.NULL : channelId);
            result.put("message_ids", ids);

            return result.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not serialize message deleted event", error);
        }
    }
}
