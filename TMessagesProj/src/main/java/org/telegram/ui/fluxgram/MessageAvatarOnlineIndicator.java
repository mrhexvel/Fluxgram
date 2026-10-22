package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;

public final class MessageAvatarOnlineIndicator {
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint onlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void draw(
            Canvas canvas,
            ImageReceiver avatarImage,
            ChatMessageCell cell,
            int account,
            Theme.ResourcesProvider resourcesProvider
    ) {
        if (!avatarImage.getVisible()) {
            return;
        }

        TLRPC.User user = resolveOnlineUser(cell, account);
        if (user == null) {
            return;
        }

        float alpha = avatarImage.getAlpha();
        if (alpha <= 0f) {
            return;
        }

        int paintAlpha = Math.round(255 * Math.min(alpha, 1f));
        float centerX = avatarImage.getImageX2() - AndroidUtilities.dp(6);
        float centerY = avatarImage.getImageY2() - AndroidUtilities.dp(6);

        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        backgroundPaint.setAlpha(paintAlpha);
        canvas.drawCircle(centerX, centerY, AndroidUtilities.dp(7), backgroundPaint);

        onlinePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider));
        onlinePaint.setAlpha(paintAlpha);
        canvas.drawCircle(centerX, centerY, AndroidUtilities.dp(5), onlinePaint);
    }

    private TLRPC.User resolveOnlineUser(ChatMessageCell cell, int account) {
        MessageObject messageObject = cell.getMessageObject();
        if (messageObject == null
                || messageObject.messageOwner == null
                || messageObject.isOutOwner()
                || messageObject.messageOwner.out
                || messageObject.isSponsored()
                || messageObject.messageOwner.fwd_from != null
                || messageObject.customAvatarDrawable != null
                || messageObject.forceAvatar
                || messageObject.messageOwner.guestchat_via_from != null
                || messageObject.isRepostPreview
                || messageObject.preview
                || messageObject.isSaved
                || !(messageObject.messageOwner.from_id instanceof TLRPC.TL_peerUser)) {
            return null;
        }

        long senderId = messageObject.messageOwner.from_id.user_id;
        if (senderId <= 0 || !avatarMatchesSender(cell, senderId, messageObject)) {
            return null;
        }

        MessagesController messagesController = MessagesController.getInstance(account);
        TLRPC.User user = messagesController.getUser(senderId);
        if (UserObject.isDeleted(user)
                || UserObject.isUserSelf(user)
                || UserObject.isBot(user)
                || UserObject.isReplyUser(user)
                || UserObject.isAnonymous(user)
                || UserObject.isService(senderId)
                || senderId == UserObject.VERIFY
                || senderId == UserObject.OAUTH
                || MessagesController.isSupportUser(user)) {
            return null;
        }

        boolean online = user.status != null
                && (user.status.expires > ConnectionsManager.getInstance(account).getCurrentTime()
                || user.status.expires <= 0
                && messagesController.onlinePrivacy.containsKey(senderId));

        return online ? user : null;
    }

    private boolean avatarMatchesSender(
            ChatMessageCell cell,
            long senderId,
            MessageObject messageObject
    ) {
        TLRPC.User boundUser = cell.getCurrentUser();
        if (boundUser != null) {
            return boundUser.id == senderId;
        }

        TLRPC.Chat boundChat = cell.getCurrentChat();
        return messageObject.messageOwner.post
                && boundChat != null
                && boundChat.signature_profiles;
    }
}
