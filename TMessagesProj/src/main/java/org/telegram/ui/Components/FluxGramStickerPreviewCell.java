package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.content.res.AppCompatResources;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fluxgram.ReplyAppearanceSettings;
import org.telegram.messenger.fluxgram.StickerSettings;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;

public final class FluxGramStickerPreviewCell extends ThemePreviewMessagesCell {
    private static final long REPLY_EMOJI_DOCUMENT_ID = 5197203181720854098L;

    private final MessageObject stickerMessage;
    private final MessageObject replyMessage;
    private int previewStickerSize;

    public FluxGramStickerPreviewCell(
            Context context,
            int account,
            INavigationLayout navigationLayout,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, navigationLayout, 0, 0, account, resourcesProvider, false);

        previewStickerSize = StickerSettings.getStickerSize();
        ChatMessageCell[] cells = getCells();
        for (ChatMessageCell cell : cells) {
            cell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            cell.setCustomStickerDrawable(createStickerDrawable(context));
        }

        stickerMessage = createStickerMessage(account);
        replyMessage = createTextMessage(
                account,
                LocaleController.getString(R.string.FluxGramStickerPreviewReplyMessage),
                false,
                -2
        );
        setReply(replyMessage, stickerMessage);

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(LocaleController.getString(R.string.FluxGramStickerPreviewDescription));
        updatePresentation();
    }

    public void updatePresentation() {
        ChatMessageCell[] cells = getCells();
        ChatMessageCell stickerCell = cells[0];
        ChatMessageCell replyCell = cells[1];

        stickerCell.setStickerTimeMode(StickerSettings.getStickerTimeMode());
        stickerCell.setStickerSize(previewStickerSize);

        int cornerRadius = 0;
        int nearCornerRadius = 0;
        int shape = StickerSettings.getStickerShape();
        if (shape == StickerSettings.SHAPE_ROUNDED) {
            cornerRadius = nearCornerRadius = AndroidUtilities.dp(6);
        } else if (shape == StickerSettings.SHAPE_MESSAGE) {
            cornerRadius = AndroidUtilities.dp(SharedConfig.bubbleRadius);
            nearCornerRadius = cornerRadius / 3;
        }
        stickerCell.setStickerCornerRadii(cornerRadius, nearCornerRadius);

        ReplyAppearanceSettings.State replyAppearance = ReplyAppearanceSettings.getState();
        for (ChatMessageCell cell : cells) {
            cell.setReplyPresentation(
                    replyAppearance.usesPeerColor(),
                    replyAppearance.showsBackgroundEmoji(),
                    replyAppearance.showsBackground()
            );
        }

        stickerMessage.forceUpdate = true;
        replyMessage.forceUpdate = true;
        stickerCell.setMessageObject(stickerMessage, null, false, false, false);
        replyCell.setMessageObject(replyMessage, null, false, false, false);
        requestLayout();
        invalidate();
    }

    public void setStickerSize(int size) {
        if (previewStickerSize == size) {
            return;
        }

        previewStickerSize = size;
        ChatMessageCell stickerCell = getCells()[0];
        stickerCell.setStickerSize(size);
        stickerCell.setMessageObject(stickerMessage, null, false, false, false);
        requestLayout();
        invalidate();
    }

    private Drawable createStickerDrawable(Context context) {
        return AppCompatResources.getDrawable(context, R.drawable.fluxgram_sticker_preview);
    }

    private MessageObject createStickerMessage(int account) {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.mime_type = "image/webp";
        document.file_reference = new byte[0];

        TLRPC.TL_documentAttributeSticker sticker = new TLRPC.TL_documentAttributeSticker();
        sticker.alt = "FG";
        document.attributes.add(sticker);

        TLRPC.TL_documentAttributeImageSize size = new TLRPC.TL_documentAttributeImageSize();
        size.w = 512;
        size.h = 510;
        document.attributes.add(size);

        TLRPC.Message message = createMessage(account, false, -1);
        message.message = "";
        message.media = new TLRPC.TL_messageMediaDocument();
        message.media.document = document;

        MessageObject messageObject = new MessageObject(account, message, true, false);
        messageObject.useCustomPhoto = true;
        messageObject.customReplyName = LocaleController.getString(R.string.FluxGramStickerPreviewSender);
        messageObject.overrideLinkColor = 5;
        messageObject.overrideLinkEmoji = REPLY_EMOJI_DOCUMENT_ID;
        return messageObject;
    }

    private MessageObject createTextMessage(int account, String text, boolean outgoing, int id) {
        TLRPC.Message message = createMessage(account, outgoing, id);
        message.message = text;
        message.media = new TLRPC.TL_messageMediaEmpty();

        MessageObject messageObject = new MessageObject(account, message, true, false);
        messageObject.customReplyName = LocaleController.getString(R.string.FluxGramStickerPreviewSender);
        messageObject.overrideLinkColor = 5;
        messageObject.overrideLinkEmoji = REPLY_EMOJI_DOCUMENT_ID;
        return messageObject;
    }

    private TLRPC.Message createMessage(int account, boolean outgoing, int id) {
        long selfId = UserConfig.getInstance(account).getClientUserId();
        TLRPC.Message message = new TLRPC.TL_message();
        message.id = id;
        message.date = (int) (System.currentTimeMillis() / 1000L);
        message.dialog_id = selfId;
        message.flags = 259;
        message.out = outgoing;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = outgoing ? selfId : 1;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = selfId;
        return message;
    }

    private void setReply(MessageObject message, MessageObject repliedMessage) {
        TLRPC.TL_messageReplyHeader replyHeader = new TLRPC.TL_messageReplyHeader();
        replyHeader.flags |= 16;
        replyHeader.reply_to_msg_id = repliedMessage.getId();
        message.messageOwner.flags |= TLRPC.MESSAGE_FLAG_REPLY;
        message.messageOwner.reply_to = replyHeader;
        message.replyMessageObject = repliedMessage;
        message.resetLayout();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(View.class.getName());
        info.setClickable(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }
}
