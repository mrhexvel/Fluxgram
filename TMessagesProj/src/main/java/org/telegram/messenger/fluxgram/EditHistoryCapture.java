package org.telegram.messenger.fluxgram;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class EditHistoryCapture {
    private EditHistoryCapture() {
    }

    private static TLRPC.Message readPrevious(MessagesStorage storage, int account, long dialogId, int messageId) {
        SQLiteCursor cursor = null;

        try {
            cursor = storage.getDatabase().queryFinalized(
                    "SELECT data, send_state FROM messages_v2 WHERE uid = ? AND mid = ?",
                    dialogId, messageId
            );

            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);

                if (data != null) {
                    try {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(true), true);
                        message.send_state = cursor.intValue(1);
                        message.readAttachPath(data, UserConfig.getInstance(account).clientUserId);
                        return message;
                    } finally {
                        data.reuse();
                    }
                }
            }
        } catch (Exception error) {
            FileLog.e(error);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }

        return null;
    }

    public static void capture(MessagesStorage storage, int account, ArrayList<TLRPC.Message> messages, boolean regularHistory) {
        if (!regularHistory || messages == null
                || !EditHistory.isEnabled(account)) {
            return;
        }

        for (TLRPC.Message incoming : messages) {
            if (!(incoming instanceof TLRPC.TL_message) || incoming.edit_date <= 0 || incoming.id <= 0) {
                continue;
            }

            long dialogId = MessageObject.getDialogId(incoming);

            if (DialogObject.isEncryptedDialog(dialogId)) {
                continue;
            }

            if (dialogId > 0 && !EditHistory.includesBots(account)) {
                TLRPC.User user = storage.getUser(dialogId);

                if (user == null || user.bot) {
                    continue;
                }
            }

            TLRPC.Message previous = readPrevious(storage, account, dialogId, incoming.id);
            EditHistory.save(account, dialogId, previous, incoming);
        }
    }

}
