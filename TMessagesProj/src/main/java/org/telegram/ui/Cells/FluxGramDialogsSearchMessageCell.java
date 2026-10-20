package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramDialogListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramDialogsSearchMessageCell extends FluxGramSearchMessageCell implements FluxGramDialogListAppearanceController.Target {
    private final FluxGramDialogListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;

    private FluxGramPeerKey requestedPeer;
    private boolean attached;

    public FluxGramDialogsSearchMessageCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            FluxGramDialogListAppearanceController controller,
            boolean avatarGeometry
    ) {
        super(context, account, resourcesProvider, avatarGeometry);
        this.controller = controller;
        renderer = new FluxGramUserListBadgeRenderer(
                this,
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS
        );
    }

    @Override
    public boolean isForumCell() {
        return false;
    }

    @Override
    public void setDialog(
            long dialogId,
            MessageObject messageObject,
            int date,
            boolean useMe,
            boolean animated
    ) {
        super.setDialog(dialogId, messageObject, date, useMe, animated);

        requestedPeer = useFromUserAsAvatar
                ? null
                : FluxGramDialogCell.peerFor(account, dialogId);
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
}
