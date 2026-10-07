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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListBadgeRenderer;

public final class FluxGramProfileMemberUserCell extends UserCell implements FluxGramUserListAppearanceController.Target {
    private final FluxGramUserListAppearanceController controller;
    private final FluxGramUserListBadgeRenderer renderer;

    private FluxGramPeerKey requestedPeer;

    public FluxGramProfileMemberUserCell(
            Context context,
            int account,
            int padding,
            Theme.ResourcesProvider resourcesProvider,
            FluxGramUserListAppearanceController controller
    ) {
        super(context, padding, 0, true, resourcesProvider);
        this.controller = controller;
        setCurrentAccount(account);
        renderer = new FluxGramUserListBadgeRenderer(
                this,
                account,
                AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS
        );
    }

    @Override
    public void setData(Object object, CharSequence name, CharSequence status, int resId, boolean divider) {
        super.setData(object, name, status, resId, divider);

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

    public void recycle() {
        requestedPeer = null;
        controller.unbind(this);
        renderer.bind(null, false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        int contentLeft = nameTextView.getTextStartX();
        int contentRight = contentLeft + nameTextView.getTextWidth();
        Drawable leftDrawable = nameTextView.getLeftDrawable();
        Drawable rightDrawable = nameTextView.getRightDrawable();
        Drawable rightDrawable2 = nameTextView.getRightDrawable2();

        contentLeft = includeDrawableLeft(contentLeft, leftDrawable);
        contentLeft = includeDrawableLeft(contentLeft, rightDrawable);
        contentLeft = includeDrawableLeft(contentLeft, rightDrawable2);
        contentRight = includeDrawableRight(contentRight, leftDrawable);
        contentRight = includeDrawableRight(contentRight, rightDrawable);
        contentRight = includeDrawableRight(contentRight, rightDrawable2);

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

    private int includeDrawableLeft(int current, Drawable drawable) {
        Rect bounds = drawable == null ? null : drawable.getBounds();
        return bounds == null || bounds.isEmpty() ? current : Math.min(current, nameTextView.getLeft() + bounds.left);
    }

    private int includeDrawableRight(int current, Drawable drawable) {
        Rect bounds = drawable == null ? null : drawable.getBounds();
        return bounds == null || bounds.isEmpty() ? current : Math.max(current, nameTextView.getLeft() + bounds.right);
    }
}
