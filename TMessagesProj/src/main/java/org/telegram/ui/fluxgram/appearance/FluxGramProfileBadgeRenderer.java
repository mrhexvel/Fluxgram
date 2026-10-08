package org.telegram.ui.fluxgram.appearance;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ViewParent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

public final class FluxGramProfileBadgeRenderer {
    private static final int BADGE_SIZE_DP = 20;
    private static final int BADGE_GAP_DP = 8;
    private static final int HIT_SIZE_DP = 32;
    private static final int DRAWABLE_GAP_DP = 4;

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

    public boolean bind(ProfileBadge nextBadge, boolean animated) {
        if (nextBadge == null) {
            if (badge == null) {
                return false;
            }

            clear(animated);
            return true;
        }
        if (nextBadge.equals(badge)) {
            return false;
        }

        boolean visibilityChanged = badge == null;
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
        drawable.setParticles(particlesEnabled(), animated);
        view.invalidate();
        return visibilityChanged;
    }

    public void draw(Canvas canvas) {
        if (badge == null || drawable == null) {
            return;
        }

        int size = AndroidUtilities.dp(BADGE_SIZE_DP);
        ProfileBadgePlacement.Result placement = getPlacement(view.getScaleX());

        if (placement == null || !placement.isVisible()) {
            hitBounds.setEmpty();
            drawable.setParticles(false, false);
            return;
        }

        int x = placement.getBadgeLeft();
        int y = getBadgeTop(size);
        drawable.setParticles(particlesEnabled(), false);
        drawable.setColor(view.getTextColor());
        drawable.setBounds(x, y, x + size, y + size);
        drawable.draw(canvas);

        int hitRadius = AndroidUtilities.dp(HIT_SIZE_DP) / 2;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        hitBounds.set(
                Math.max(placement.getContentRight(), centerX - hitRadius),
                Math.max(0, centerY - hitRadius),
                Math.min(placement.getAvailableRight(), centerX + hitRadius),
                Math.min(view.getHeight(), centerY + hitRadius)
        );
        if (hitBounds.left >= hitBounds.right || hitBounds.top >= hitBounds.bottom) {
            hitBounds.setEmpty();
        }
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

    public ProfileBadgePlacement.Result getPlacement(float scale) {
        if (badge == null) {
            return null;
        }

        return ProfileBadgePlacement.calculate(
                view.getTextWidth(),
                getDrawableWidth(view.getLeftDrawable()),
                getDrawableWidth(view.getRightDrawable()),
                getDrawableWidth(view.getRightDrawable2()),
                AndroidUtilities.dp(DRAWABLE_GAP_DP),
                AndroidUtilities.dp(BADGE_GAP_DP),
                AndroidUtilities.dp(BADGE_SIZE_DP),
                view.getPaddingLeft(),
                view.getPaddingRight(),
                view.getWidth(),
                scale
        );
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

    private int getDrawableWidth(Drawable drawable) {
        if (drawable == null) {
            return 0;
        }

        return drawable.getIntrinsicWidth();
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

    private boolean particlesEnabled() {
        return badge != null
                && badge.hasParticles()
                && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES);
    }

    private void clear(boolean animated) {
        badge = null;
        hitBounds.setEmpty();
        cancelTouch();

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
