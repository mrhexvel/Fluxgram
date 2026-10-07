package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.messenger.fluxgram.appearance.TelegramPeerKeyAdapter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramDialogListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramDialogsSearchProfileCell extends ProfileSearchCell implements FluxGramDialogListAppearanceController.Target {
    private final FluxGramDialogListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;

    private FluxGramPeerKey requestedPeer;
    private boolean sponsored;
    private boolean attached;

    public FluxGramDialogsSearchProfileCell(
            Context context,
            int account,
            FluxGramDialogListAppearanceController controller
    ) {
        super(context);
        this.controller = controller;
        setCurrentAccount(account);
        renderer = new FluxGramUserListBadgeRenderer(
                this,
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS
        );
    }

    @Override
    public void setAd(TLRPC.TL_sponsoredPeer sponsoredPeer) {
        super.setAd(sponsoredPeer);
        sponsored = sponsoredPeer != null;
    }

    @Override
    public void setData(
            Object object,
            TLRPC.EncryptedChat encryptedChat,
            CharSequence name,
            CharSequence status,
            boolean needCount,
            boolean saved
    ) {
        super.setData(object, encryptedChat, name, status, needCount, saved);

        requestedPeer = sponsored ? null : peerFor(object, encryptedChat, saved);
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
        sponsored = false;
        controller.unbind(this);
        renderer.bind(null, false);
        renderer.detach();
        attached = false;
    }

    @Override
    protected void drawAfterNameStatus(
            Canvas canvas,
            int contentLeft,
            int contentRight,
            int availableLeft,
            int availableRight,
            int centerY
    ) {
        renderer.draw(
                canvas,
                LocaleController.isRTL,
                contentLeft,
                contentRight,
                availableLeft,
                availableRight,
                centerY,
                Theme.getColor(Theme.key_chats_name)
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

    private static FluxGramPeerKey peerFor(
            Object object,
            TLRPC.EncryptedChat encryptedChat,
            boolean saved
    ) {
        if (encryptedChat != null || saved) {
            return null;
        }
        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            if (user.id <= 0
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
        if (!(object instanceof TLRPC.Chat)) {
            return null;
        }

        TLRPC.Chat chat = (TLRPC.Chat) object;
        if (ChatObject.isCommunity(chat)
                || ChatObject.isChannelAndNotMegaGroup(chat)
                || chat.broadcast) {
            return null;
        }

        FluxGramPeerKey peer = TelegramPeerKeyAdapter.fromChat(chat);
        return peer != null && peer.getType() == FluxGramPeerKey.Type.GROUP ? peer : null;
    }
}
