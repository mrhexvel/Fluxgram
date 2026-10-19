package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.AvatarGeometry;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.messenger.fluxgram.appearance.TelegramPeerKeyAdapter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.fluxgram.appearance.FluxGramDialogListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramDialogCell extends DialogCell implements FluxGramDialogListAppearanceController.Target {
    private final int account;
    private final FluxGramDialogListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;
    private boolean senderMiniAvatars;
    private boolean avatarGeometryEnabled;
    private AvatarSpan messageAvatarSpan;

    private FluxGramPeerKey requestedPeer;
    private boolean attached;

    public FluxGramDialogCell(
            DialogsActivity fragment,
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            FluxGramDialogListAppearanceController controller,
            boolean senderMiniAvatars,
            boolean avatarGeometryEnabled
    ) {
        super(fragment, context, true, false, account, resourcesProvider);
        this.account = account;
        this.controller = controller;
        this.senderMiniAvatars = senderMiniAvatars;
        this.avatarGeometryEnabled = avatarGeometryEnabled;
        renderer = new FluxGramUserListBadgeRenderer(
                this,
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS
        );
    }

    public void setSenderMiniAvatars(boolean shown) {
        senderMiniAvatars = shown;
    }

    public void setAvatarGeometryEnabled(boolean enabled) {
        avatarGeometryEnabled = enabled;
    }

    @Override
    protected int resolveAvatarRoundRadius(int stockRadius, int avatarSize, int avatarRole) {
        if (!avatarGeometryEnabled) {
            return stockRadius;
        }

        return AvatarGeometry.resolveRadius(account, avatarSize, avatarRole, stockRadius);
    }

    @Override
    protected float resolveStoryRoundRectRadius(int avatarRadius, int avatarRole) {
        if (!avatarGeometryEnabled || AvatarGeometry.usesStockGeometry(account)) {
            return -1;
        }

        return avatarRadius;
    }

    @Override
    protected CharSequence decorateMessageSender(CharSequence sender, MessageObject messageObject) {
        if (!senderMiniAvatars || messageObject == null || TextUtils.isEmpty(sender)) {
            return sender;
        }

        long fromDialogId = getMessageSenderAvatarDialogId(messageObject);
        if (fromDialogId == 0
                || fromDialogId == INVALID_MESSAGE_SENDER_DIALOG_ID
                || fromDialogId == UserConfig.getInstance(account).getClientUserId()) {
            return sender;
        }

        if (messageAvatarSpan == null) {
            messageAvatarSpan = new AvatarSpan(this, account, 18);
            messageAvatarSpan.needDrawShadow = false;
        }
        messageAvatarSpan.setDialogId(fromDialogId);

        SpannableStringBuilder decorated = new SpannableStringBuilder("A ");
        decorated.setSpan(messageAvatarSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int spacerStart = decorated.length();
        decorated.append(' ');
        decorated.setSpan(
                new DialogCell.FixedWidthSpan(dp(1)),
                spacerStart,
                decorated.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        decorated.append(sender);

        return decorated;
    }

    @Override
    public void setDialog(TLRPC.Dialog dialog, int type, int folder) {
        super.setDialog(dialog, type, folder);

        requestedPeer = peerFor(account, dialog.id);
        if (attached) {
            controller.bind(this, requestedPeer);
        } else {
            renderer.bind(null, false);
        }
    }

    @Override
    public FluxGramPeerKey getRequestedPeer() {
        return requestedPeer;
    }

    @Override
    public void apply(ProfileBadge badge, boolean animated) {
        if (attached) {
            renderer.bind(badge, animated);
        }
    }

    @Override
    public void destroyAppearance() {
        requestedPeer = null;
        renderer.destroy();
    }

    public void recycleAppearance() {
        requestedPeer = null;
        controller.unbind(this);
        renderer.bind(null, false);
        renderer.detach();
        attached = false;
    }

    @Override
    protected void drawAdditionalNameStatus(
            Canvas canvas,
            int contentLeft,
            int contentRight,
            int availableLeft,
            int availableRight,
            int centerY,
            int color
    ) {
        renderer.draw(
                canvas,
                LocaleController.isRTL,
                contentLeft,
                contentRight,
                availableLeft,
                availableRight,
                centerY,
                color
        );
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        renderer.attach();
        controller.bind(this, requestedPeer);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        controller.unbind(this);
        renderer.detach();
        super.onDetachedFromWindow();
    }

    static FluxGramPeerKey peerFor(int account, long dialogId) {
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            if (user == null
                    || user.id <= 0
                    || UserObject.isDeleted(user)
                    || UserObject.isUserSelf(user)
                    || UserObject.isReplyUser(user)
                    || UserObject.isAnonymous(user)
                    || UserObject.isService(user.id)
                    || user.id == UserObject.VERIFY
                    || user.id == UserObject.OAUTH) {
                return null;
            }

            return TelegramPeerKeyAdapter.fromUserId(user.id);
        }
        if (!DialogObject.isChatDialog(dialogId)) {
            return null;
        }

        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null
                || ChatObject.isCommunity(chat)
                || ChatObject.isChannelAndNotMegaGroup(chat)
                || chat.broadcast) {
            return null;
        }

        FluxGramPeerKey peer = TelegramPeerKeyAdapter.fromChat(chat);
        return peer != null && peer.getType() == FluxGramPeerKey.Type.GROUP ? peer : null;
    }
}
