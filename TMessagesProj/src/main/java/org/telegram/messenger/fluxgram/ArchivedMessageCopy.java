package org.telegram.messenger.fluxgram;

import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

public final class ArchivedMessageCopy {
    private ArchivedMessageCopy() {
    }

    public static boolean contains(ArrayList<MessageObject> messages) {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);

            if (message != null && message.messageOwner.fluxgramDeleted) {
                return true;
            }
        }

        return false;
    }

    public static boolean canSendSelection(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        boolean archived = messages.get(0) != null && messages.get(0).messageOwner.fluxgramDeleted;

        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);

            if (message == null
                    || message.messageOwner.fluxgramDeleted != archived
                    || !message.canForwardMessage()) {
                return false;
            }
        }

        return true;
    }

    public static int checkDestination(AccountInstance accountInstance, long peer, MessageObject message) {
        if (!message.canSendAsCopy()) {
            return 18;
        }

        TLRPC.Chat chat = DialogObject.isChatDialog(peer)
                ? accountInstance.getMessagesController().getChat(-peer)
                : null;

        if (chat != null) {
            return SendMessagesHelper.canSendMessageToChat(chat, message);
        }

        long userId = peer;

        if (DialogObject.isEncryptedDialog(peer)) {
            TLRPC.EncryptedChat encryptedChat = accountInstance.getMessagesController()
                    .getEncryptedChat(DialogObject.getEncryptedChatId(peer));

            if (encryptedChat == null) {
                return 18;
            }

            userId = encryptedChat.user_id;
        }

        TLRPC.UserFull userFull = accountInstance.getMessagesController().getUserFull(userId);

        if (userFull != null && userFull.voice_messages_forbidden) {
            if (MessageObject.isVoiceMessage(message.messageOwner)) {
                return 7;
            }

            if (MessageObject.isRoundVideoMessage(message.messageOwner)) {
                return 8;
            }
        }

        return 0;
    }

    public static void send(
            AccountInstance accountInstance,
            ArrayList<MessageObject> messages,
            long peer,
            boolean hideCaption,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            MessageObject replyToTopMsg,
            long payStars,
            long monoForumPeerId,
            MessageSuggestionParams suggestionParams) {
        ArrayList<MessageObject> source = new ArrayList<>(messages);

        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<SendMessagesHelper.SendMessageParams> prepared = new ArrayList<>();

            for (int i = 0; i < source.size(); i++) {
                SendMessagesHelper.SendMessageParams params = prepare(
                        accountInstance,
                        source.get(i),
                        peer,
                        hideCaption,
                        notify,
                        scheduleDate,
                        scheduleRepeatPeriod,
                        replyToTopMsg,
                        payStars,
                        monoForumPeerId,
                        i == 0 ? suggestionParams : null);

                if (params == null) {
                    return;
                }

                prepared.add(params);
            }

            AndroidUtilities.runOnUIThread(() -> {
                for (int i = 0; i < prepared.size(); i++) {
                    accountInstance.getSendMessagesHelper().sendMessage(prepared.get(i));
                }
            });
        });
    }

    private static SendMessagesHelper.SendMessageParams prepare(
            AccountInstance accountInstance,
            MessageObject message,
            long peer,
            boolean hideCaption,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            MessageObject replyToTopMsg,
            long payStars,
            long monoForumPeerId,
            MessageSuggestionParams suggestionParams) {
        TLRPC.MessageMedia media = MessageObject.getMedia(message.messageOwner);
        boolean hasCaption = media instanceof TLRPC.TL_messageMediaPhoto
                || media instanceof TLRPC.TL_messageMediaDocument;
        String text = hideCaption && hasCaption ? "" : message.messageOwner.message;
        ArrayList<TLRPC.MessageEntity> entities = hideCaption && hasCaption
                ? null
                : copyEntities(message.messageOwner.entities);

        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            TLRPC.TL_photo photo = accountInstance.getSendMessagesHelper()
                    .generatePhotoSizes(message.messageOwner.attachPath, null);

            if (photo == null) {
                return null;
            }

            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                    photo,
                    message.messageOwner.attachPath,
                    peer,
                    null,
                    replyToTopMsg,
                    text,
                    entities,
                    null,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    0,
                    null,
                    false,
                    message.hasMediaSpoilers());
            applyCommon(params, message, payStars, monoForumPeerId, suggestionParams);
            return params;
        }

        SendMessagesHelper.SendMessageParams params;

        if (media instanceof TLRPC.TL_messageMediaDocument && media.document instanceof TLRPC.TL_document) {
            TLRPC.TL_document document = (TLRPC.TL_document) TLObject.deepCopy(
                    media.document,
                    TLRPC.Document::TLdeserialize);
            document.id = 0;
            document.access_hash = 0;
            document.dc_id = 0;
            document.file_reference = new byte[0];
            document.thumbs.clear();
            document.video_thumbs.clear();
            document.flags &= ~3;
            params = SendMessagesHelper.SendMessageParams.of(
                    document,
                    null,
                    message.messageOwner.attachPath,
                    peer,
                    null,
                    replyToTopMsg,
                    text,
                    entities,
                    null,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    0,
                    null,
                    null,
                    false,
                    message.hasMediaSpoilers());
        } else if (media instanceof TLRPC.TL_messageMediaVenue || media instanceof TLRPC.TL_messageMediaGeo) {
            params = SendMessagesHelper.SendMessageParams.of(
                    media,
                    peer,
                    null,
                    replyToTopMsg,
                    null,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod);
        } else if (media instanceof TLRPC.TL_messageMediaContact) {
            TLRPC.User user = new TLRPC.TL_userContact_old2();
            user.phone = media.phone_number;
            user.first_name = media.first_name;
            user.last_name = media.last_name;
            user.id = media.user_id;

            if (!TextUtils.isEmpty(media.vcard)) {
                TLRPC.RestrictionReason reason = new TLRPC.RestrictionReason();
                reason.platform = "";
                reason.reason = "";
                reason.text = media.vcard;
                user.restriction_reason.add(reason);
            }

            params = SendMessagesHelper.SendMessageParams.of(
                    user,
                    peer,
                    null,
                    replyToTopMsg,
                    null,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod);
        } else {
            TLRPC.WebPage webPage = media instanceof TLRPC.TL_messageMediaWebPage ? media.webpage : null;
            params = SendMessagesHelper.SendMessageParams.of(
                    text,
                    peer,
                    null,
                    replyToTopMsg,
                    webPage,
                    webPage != null,
                    entities,
                    null,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    null,
                    false);
        }

        applyCommon(params, message, payStars, monoForumPeerId, suggestionParams);
        return params;
    }

    private static void applyCommon(
            SendMessagesHelper.SendMessageParams params,
            MessageObject message,
            long payStars,
            long monoForumPeerId,
            MessageSuggestionParams suggestionParams) {
        params.payStars = payStars;
        params.monoForumPeer = monoForumPeerId;
        params.suggestionParams = suggestionParams;
        params.invert_media = message.messageOwner.invert_media;
    }

    private static ArrayList<TLRPC.MessageEntity> copyEntities(ArrayList<TLRPC.MessageEntity> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();

        for (int i = 0; i < source.size(); i++) {
            TLRPC.MessageEntity entity = source.get(i);

            if (entity instanceof TLRPC.TL_messageEntityBold
                    || entity instanceof TLRPC.TL_messageEntityItalic
                    || entity instanceof TLRPC.TL_messageEntityUnderline
                    || entity instanceof TLRPC.TL_messageEntityStrike
                    || entity instanceof TLRPC.TL_messageEntityBlockquote
                    || entity instanceof TLRPC.TL_messageEntityPre
                    || entity instanceof TLRPC.TL_messageEntityCode
                    || entity instanceof TLRPC.TL_messageEntityTextUrl
                    || entity instanceof TLRPC.TL_messageEntitySpoiler
                    || entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                entities.add(entity);
            }
        }

        return entities.isEmpty() ? null : entities;
    }

    public static boolean hasLocalAttachment(MessageObject message) {
        File file = new File(message.messageOwner.attachPath);
        return file.isFile()
                && file.length() > 0
                && FileLoader.checkUploadFileSize(message.currentAccount, file.length());
    }
}
