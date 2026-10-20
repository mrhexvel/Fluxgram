package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.AvatarGeometry;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramDialogListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public class FluxGramShareDialogCell extends ShareDialogCell implements FluxGramDialogListAppearanceController.Target {
    private final int account;
    private final FluxGramDialogListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;
    private final boolean avatarGeometry;

    private FluxGramPeerKey requestedPeer;
    private long dialogId;
    private boolean topicVisible;
    private boolean attached;

    public FluxGramShareDialogCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            FluxGramDialogListAppearanceController controller,
            boolean avatarGeometry
    ) {
        super(context, TYPE_SHARE, account, resourcesProvider);
        this.account = account;
        this.controller = controller;
        this.avatarGeometry = avatarGeometry;
        renderer = new FluxGramUserListBadgeRenderer(
                this,
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS
        );
    }

    @Override
    public void setDialog(long uid, boolean checked, CharSequence name) {
        super.setDialog(uid, checked, name);

        dialogId = uid;
        updateRequestedPeer();
    }

    @Override
    public void setTopic(TLRPC.TL_forumTopic topic, boolean mono, boolean animate) {
        super.setTopic(topic, mono, animate);

        topicVisible = topic != null;
        updateRequestedPeer();
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
        dialogId = 0;
        topicVisible = false;
        if (controller != null) {
            controller.unbind(this);
        }
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

    @Override
    protected int resolveAvatarRoundRadius(
            int stockRadius,
            int avatarSize,
            long dialogId,
            TLRPC.User user,
            TLRPC.Chat chat,
            boolean topicVisible
    ) {
        if (!avatarGeometry || topicVisible || !isAvatarGeometryPeer(dialogId, user, chat)) {
            return stockRadius;
        }

        int role;
        if (chat == null) {
            role = AvatarGeometry.ROLE_USER;
        } else if (ChatObject.isMonoForum(chat)) {
            role = AvatarGeometry.ROLE_MONOFORUM;
        } else if (ChatObject.isCommunity(chat)) {
            role = AvatarGeometry.ROLE_COMMUNITY;
        } else if (ChatObject.isForum(chat)) {
            role = AvatarGeometry.ROLE_FORUM;
        } else {
            role = AvatarGeometry.ROLE_GROUP;
        }
        return AvatarGeometry.resolveRadius(account, avatarSize, role, stockRadius);
    }

    private void updateRequestedPeer() {
        requestedPeer = topicVisible ? null : FluxGramDialogCell.peerFor(account, dialogId);
        if (attached && controller != null) {
            controller.bind(this, requestedPeer);
        } else {
            renderer.bind(null, false);
        }
    }

    private static boolean isAvatarGeometryPeer(long dialogId, TLRPC.User user, TLRPC.Chat chat) {
        if (dialogId == Long.MAX_VALUE) {
            return false;
        }
        if (chat != null) {
            return chat.id > 0;
        }
        return user != null
                && user.id > 0
                && !UserObject.isDeleted(user)
                && !UserObject.isUserSelf(user)
                && !UserObject.isReplyUser(user)
                && !UserObject.isAnonymous(user)
                && !UserObject.isService(user.id)
                && user.id != UserObject.VERIFY
                && user.id != UserObject.OAUTH;
    }
}
