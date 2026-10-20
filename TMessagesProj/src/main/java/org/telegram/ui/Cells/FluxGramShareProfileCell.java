package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramDialogListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramShareProfileCell extends FluxGramSearchProfileCell implements FluxGramDialogListAppearanceController.Target {
    private final Theme.ResourcesProvider resourcesProvider;
    private final FluxGramDialogListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;

    private FluxGramPeerKey requestedPeer;
    private boolean attached;

    public FluxGramShareProfileCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            FluxGramDialogListAppearanceController controller,
            boolean avatarGeometry
    ) {
        super(context, account, resourcesProvider, avatarGeometry);
        this.resourcesProvider = resourcesProvider;
        this.controller = controller;
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

        requestedPeer = encryptedChat == null && !saved ? peerFor(object) : null;
        if (attached && controller != null) {
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
        if (controller != null) {
            controller.unbind(this);
        }
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
                Theme.getColor(Theme.key_chats_name, resourcesProvider)
        );
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        renderer.attach();
        if (controller != null) {
            controller.bind(this, requestedPeer);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        if (controller != null) {
            controller.unbind(this);
        }
        renderer.detach();
        super.onDetachedFromWindow();
    }

    private FluxGramPeerKey peerFor(Object object) {
        if (object instanceof TLRPC.User) {
            return FluxGramDialogCell.peerFor(account, ((TLRPC.User) object).id);
        }
        if (object instanceof TLRPC.Chat) {
            return FluxGramDialogCell.peerFor(account, -((TLRPC.Chat) object).id);
        }
        return null;
    }
}
