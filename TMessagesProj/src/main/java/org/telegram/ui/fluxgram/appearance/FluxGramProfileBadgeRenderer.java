package org.telegram.ui.fluxgram.appearance;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ViewParent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

public final class FluxGramProfileBadgeRenderer {
    private static final int BADGE_SIZE_DP = 24;
    private static final int BADGE_GAP_DP = 8;
    private static final int HIT_SIZE_DP = 32;

    private final BaseFragment fragment;
    private final int account;
    private final SimpleTextView view;
    private final int cacheType;
    private final Rect hitBounds = new Rect();

    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable drawable;
    private ProfileBadge badge;
    private boolean attached;
    private boolean trackingTouch;
    private float touchDownX;
    private float touchDownY;

    public FluxGramProfileBadgeRenderer(
            BaseFragment fragment,
            int account,
            SimpleTextView view,
            int cacheType
    ) {
        this.fragment = fragment;
        this.account = account;
        this.view = view;
        this.cacheType = cacheType;
    }

    public void bind(ProfileBadge nextBadge, boolean animated) {
        if (nextBadge == null) {
            clear(animated);
            return;
        }
        if (nextBadge.equals(badge)) {
            return;
        }

        badge = nextBadge;

        if (drawable == null) {
            drawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(
                    view,
                    AndroidUtilities.dp(BADGE_SIZE_DP),
                    cacheType
            );
            drawable.setCurrentAccount(account);

            if (attached) {
                drawable.attach();
            }
        }

        drawable.set(nextBadge.getDocumentId(), animated);
        drawable.setParticles(true, animated);
        view.setMinusWidth(AndroidUtilities.dp(BADGE_SIZE_DP + BADGE_GAP_DP));
        view.invalidate();
    }

    public void draw(Canvas canvas) {
        if (badge == null || drawable == null) {
            return;
        }

        int gap = AndroidUtilities.dp(BADGE_GAP_DP);
        int size = AndroidUtilities.dp(BADGE_SIZE_DP);
        int x = getContentRight() + gap;
        int availableRight = view.getWidth() - view.getPaddingRight();

        if (x + size > availableRight) {
            hitBounds.setEmpty();
            return;
        }

        int y = getBadgeTop(size);
        drawable.setColor(view.getTextColor());
        drawable.setBounds(x, y, x + size, y + size);
        drawable.draw(canvas);

        int hitRadius = AndroidUtilities.dp(HIT_SIZE_DP) / 2;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        hitBounds.set(
                centerX - hitRadius,
                centerY - hitRadius,
                centerX + hitRadius,
                centerY + hitRadius
        );
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (badge == null || hitBounds.isEmpty()) {
            return false;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (event.getPointerCount() != 1
                    || !hitBounds.contains((int) event.getX(), (int) event.getY())) {
                return false;
            }

            trackingTouch = true;
            touchDownX = event.getX();
            touchDownY = event.getY();
            requestParentTouch(true);
            return true;
        }

        if (!trackingTouch) {
            return false;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            float distanceX = Math.abs(event.getX() - touchDownX);
            float distanceY = Math.abs(event.getY() - touchDownY);

            if (event.getPointerCount() != 1
                    || distanceX >= AndroidUtilities.touchSlop
                    || distanceY >= AndroidUtilities.touchSlop) {
                cancelTouch();
            }

            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            ProfileBadge selectedBadge = badge;
            cancelTouch();

            if (hitBounds.contains((int) event.getX(), (int) event.getY())) {
                FluxGramBadgeBulletin.show(fragment, account, selectedBadge);
            }

            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
            cancelTouch();
            return true;
        }

        return true;
    }

    public String getAccessibilityLabel() {
        return badge == null ? null : badge.getTitle();
    }

    public void attach() {
        if (attached) {
            return;
        }

        attached = true;
        if (drawable != null) {
            drawable.attach();
        }
    }

    public void detach() {
        if (!attached) {
            return;
        }

        attached = false;
        cancelTouch();
        if (drawable != null) {
            drawable.detach();
        }
    }

    public void destroy() {
        detach();
        clear(false);
        drawable = null;
    }

    private int getContentRight() {
        int contentRight = view.getPaddingLeft() + view.getTextWidth();
        Drawable leftDrawable = view.getLeftDrawable();
        Drawable rightDrawable = view.getRightDrawable();
        Drawable rightDrawable2 = view.getRightDrawable2();

        if (leftDrawable != null && !leftDrawable.getBounds().isEmpty()) {
            contentRight += leftDrawable.getBounds().right + AndroidUtilities.dp(4);
        }
        if (rightDrawable != null && !rightDrawable.getBounds().isEmpty()) {
            contentRight = Math.max(contentRight, rightDrawable.getBounds().right);
        }
        if (rightDrawable2 != null && !rightDrawable2.getBounds().isEmpty()) {
            contentRight = Math.max(contentRight, rightDrawable2.getBounds().right);
        }

        return contentRight;
    }

    private int getBadgeTop(int size) {
        Drawable rightDrawable = view.getRightDrawable();
        if (rightDrawable != null && !rightDrawable.getBounds().isEmpty()) {
            return rightDrawable.getBounds().centerY() - size / 2;
        }

        Drawable rightDrawable2 = view.getRightDrawable2();
        if (rightDrawable2 != null && !rightDrawable2.getBounds().isEmpty()) {
            return rightDrawable2.getBounds().centerY() - size / 2;
        }

        return view.getPaddingTop() + (view.getTextHeight() - size) / 2;
    }

    private void clear(boolean animated) {
        badge = null;
        hitBounds.setEmpty();
        cancelTouch();
        view.setMinusWidth(0);

        if (drawable != null) {
            drawable.setParticles(false, animated);
            drawable.set((Drawable) null, animated);
        }

        view.invalidate();
    }

    private void cancelTouch() {
        if (!trackingTouch) {
            return;
        }

        trackingTouch = false;
        requestParentTouch(false);
    }

    private void requestParentTouch(boolean disallow) {
        ViewParent parent = view.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }
}
