package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.messenger.fluxgram.appearance.TelegramPeerKeyAdapter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
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
            Theme.ResourcesProvider resourcesProvider,
            FluxGramUserListAppearanceController controller
    ) {
        super(context, avatarPadding, namePadding, needOption, resourcesProvider);
        this.controller = controller;
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
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        SimpleTextView nameTextView = getNameTextView();
        int contentLeft = nameTextView.getTextStartX();
        int contentRight = contentLeft + nameTextView.getTextWidth();
        Drawable leftDrawable = nameTextView.getLeftDrawable();
        Drawable rightDrawable = nameTextView.getRightDrawable();
        Drawable rightDrawable2 = nameTextView.getRightDrawable2();

        contentLeft = includeDrawableLeft(contentLeft, nameTextView, leftDrawable);
        contentLeft = includeDrawableLeft(contentLeft, nameTextView, rightDrawable);
        contentLeft = includeDrawableLeft(contentLeft, nameTextView, rightDrawable2);
        contentRight = includeDrawableRight(contentRight, nameTextView, leftDrawable);
        contentRight = includeDrawableRight(contentRight, nameTextView, rightDrawable);
        contentRight = includeDrawableRight(contentRight, nameTextView, rightDrawable2);

        renderer.draw(
                canvas,
                LocaleController.isRTL,
                contentLeft,
                contentRight,
                nameTextView.getLeft() + nameTextView.getPaddingLeft(),
                nameTextView.getRight() - nameTextView.getPaddingRight(),
                nameTextView.getTop() + nameTextView.getHeight() / 2,
                nameTextView.getTextColor()
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

    private static int includeDrawableLeft(int current, SimpleTextView nameTextView, Drawable drawable) {
        Rect bounds = drawable == null ? null : drawable.getBounds();
        return bounds == null || bounds.isEmpty() ? current : Math.min(current, nameTextView.getLeft() + bounds.left);
    }

    private static int includeDrawableRight(int current, SimpleTextView nameTextView, Drawable drawable) {
        Rect bounds = drawable == null ? null : drawable.getBounds();
        return bounds == null || bounds.isEmpty() ? current : Math.max(current, nameTextView.getLeft() + bounds.right);
    }
}
