package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramDialogListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramShareHintCell extends HintDialogCell implements FluxGramDialogListAppearanceController.Target {
    private final int account;
    private final FluxGramDialogListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;

    private FluxGramPeerKey requestedPeer;
    private boolean attached;

    public FluxGramShareHintCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            FluxGramDialogListAppearanceController controller
    ) {
        super(context, true, resourcesProvider);
        this.account = account;
        this.controller = controller;
        setCurrentAccount(account);
        renderer = new FluxGramUserListBadgeRenderer(
                this,
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS
        );
    }

    @Override
    public void setDialog(long dialogId, boolean counter, CharSequence name) {
        super.setDialog(dialogId, counter, name);

        requestedPeer = FluxGramDialogCell.peerFor(account, dialogId);
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
    protected void drawAfterName(
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
