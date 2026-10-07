package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.messenger.fluxgram.appearance.TelegramPeerKeyAdapter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramContactsSearchCell extends ProfileSearchCell implements FluxGramUserListAppearanceController.Target {
    private final FluxGramUserListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;

    private FluxGramPeerKey requestedPeer;

    public FluxGramContactsSearchCell(
            Context context,
            int account,
            FluxGramUserListAppearanceController controller
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
    public void setData(
            Object object,
            TLRPC.EncryptedChat encryptedChat,
            CharSequence name,
            CharSequence status,
            boolean needCount,
            boolean saved
    ) {
        super.setData(object, encryptedChat, name, status, needCount, saved);

        requestedPeer = peerFor(object, encryptedChat, saved);
        controller.bind(this, requestedPeer);
    }

    @Override
    public FluxGramPeerKey getRequestedPeer() {
        return requestedPeer;
    }

    @Override
    public void apply(ProfileBadge badge, boolean animated) {
        renderer.bind(badge, animated);
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
        renderer.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        renderer.detach();
        super.onDetachedFromWindow();
    }

    private static FluxGramPeerKey peerFor(
            Object object,
            TLRPC.EncryptedChat encryptedChat,
            boolean saved
    ) {
        if (!(object instanceof TLRPC.User) || encryptedChat != null || saved) {
            return null;
        }

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
}
