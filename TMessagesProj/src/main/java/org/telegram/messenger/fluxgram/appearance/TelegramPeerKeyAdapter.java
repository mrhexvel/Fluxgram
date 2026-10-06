package org.telegram.messenger.fluxgram.appearance;

import org.telegram.messenger.ChatObject;
import org.telegram.tgnet.TLRPC;

public final class TelegramPeerKeyAdapter {
    private TelegramPeerKeyAdapter() {
    }

    public static FluxGramPeerKey fromUserId(long userId) {
        if (userId <= 0) {
            return null;
        }

        return new FluxGramPeerKey(FluxGramPeerKey.Type.USER, userId);
    }

    public static FluxGramPeerKey fromChat(TLRPC.Chat chat) {
        if (chat == null || chat.id <= 0 || ChatObject.isCommunity(chat)) {
            return null;
        }

        FluxGramPeerKey.Type type;
        if (ChatObject.isChannel(chat) && !chat.megagroup) {
            type = FluxGramPeerKey.Type.CHANNEL;
        } else {
            type = FluxGramPeerKey.Type.GROUP;
        }

        return new FluxGramPeerKey(type, chat.id);
    }
}
