package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.messenger.fluxgram.appearance.TelegramPeerKeyAdapter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramChatUsersUserCell extends ManageChatUserCell implements FluxGramUserListAppearanceController.Target {
    private final FluxGramUserListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;

    private FluxGramPeerKey requestedPeer;

    public FluxGramChatUsersUserCell(
            Context context,
            int account,
            int avatarPadding,
            int namePadding,
            boolean needOption,
            FluxGramUserListAppearanceController controller
    ) {
        super(context, avatarPadding, namePadding, needOption);
        this.controller = controller;
        setCurrentAccount(account);
        renderer = new FluxGramUserListBadgeRenderer(
                this,
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS
        );
    }

    @Override
    public void setData(Object object, CharSequence name, CharSequence status, boolean divider) {
        super.setData(object, name, status, divider);

        requestedPeer = peerFor(object);
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

    @Override
    public void recycle() {
        requestedPeer = null;
        controller.unbind(this);
        renderer.bind(null, false);
        super.recycle();
    }

    @Override
    protected void drawAfterNameStatus(
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
        renderer.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        renderer.detach();
        super.onDetachedFromWindow();
    }

    private static FluxGramPeerKey peerFor(Object object) {
        if (!(object instanceof TLRPC.User)) {
            return null;
        }

        TLRPC.User user = (TLRPC.User) object;
        if (user.id <= 0
                || UserObject.isDeleted(user)
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
