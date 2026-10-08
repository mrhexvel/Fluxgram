package org.telegram.ui.fluxgram.appearance;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

public final class FluxGramUserListBadgeRenderer {
    private static final int BADGE_SIZE_DP = 16;
    private static final int BADGE_GAP_DP = 4;

    private final View view;
    private final int account;
    private final int cacheType;

    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable drawable;
    private ProfileBadge badge;
    private boolean attached;

    public FluxGramUserListBadgeRenderer(View view, int account, int cacheType) {
        this.view = view;
        this.account = account;
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
        drawable.setParticles(particlesEnabled(), animated);
        view.invalidate();
    }

    public void draw(
            Canvas canvas,
            boolean rtl,
            int contentLeft,
            int contentRight,
            int availableLeft,
            int availableRight,
            int centerY,
            int color
    ) {
        if (badge == null || drawable == null) {
            return;
        }

        int size = AndroidUtilities.dp(BADGE_SIZE_DP);
        int badgeLeft = UserListBadgePlacement.calculate(
                rtl,
                contentLeft,
                contentRight,
                availableLeft,
                availableRight,
                AndroidUtilities.dp(BADGE_GAP_DP),
                size
        );

        if (badgeLeft == UserListBadgePlacement.HIDDEN) {
            drawable.setParticles(false, false);
            return;
        }

        int badgeTop = centerY - size / 2;
        drawable.setColor(color);
        drawable.setParticles(particlesEnabled(), false);
        drawable.setBounds(badgeLeft, badgeTop, badgeLeft + size, badgeTop + size);
        drawable.draw(canvas);
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
        if (drawable != null) {
            drawable.detach();
        }
    }

    public void destroy() {
        detach();
        clear(false);
        drawable = null;
    }

    private boolean particlesEnabled() {
        return badge != null
                && badge.hasParticles()
                && LiteMode.isEnabled(LiteMode.FLAG_PARTICLES);
    }

    private void clear(boolean animated) {
        badge = null;

        if (drawable != null) {
            drawable.setParticles(false, animated);
            drawable.set((Drawable) null, animated);
        }

        view.invalidate();
    }
}
