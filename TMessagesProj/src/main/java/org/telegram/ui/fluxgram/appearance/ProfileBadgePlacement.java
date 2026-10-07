package org.telegram.ui.fluxgram.appearance;

public final class ProfileBadgePlacement {
    public static final int HIDDEN = -1;

    public static final class Result {
        private final int contentRight;
        private final int badgeLeft;
        private final int availableRight;
        private final float scaledCenterX;

        private Result(
                int contentRight,
                int badgeLeft,
                int availableRight,
                float scaledCenterX
        ) {
            this.contentRight = contentRight;
            this.badgeLeft = badgeLeft;
            this.availableRight = availableRight;
            this.scaledCenterX = scaledCenterX;
        }

        public boolean isVisible() {
            return badgeLeft != HIDDEN;
        }

        public int getContentRight() {
            return contentRight;
        }

        public int getBadgeLeft() {
            return badgeLeft;
        }

        public int getAvailableRight() {
            return availableRight;
        }

        public float getScaledCenterX() {
            return scaledCenterX;
        }
    }

    private ProfileBadgePlacement() {
    }

    public static Result calculate(
            int textWidth,
            int leftDrawableWidth,
            int firstRightDrawableWidth,
            int secondRightDrawableWidth,
            int drawableGap,
            int badgeGap,
            int badgeSize,
            int paddingLeft,
            int paddingRight,
            int viewWidth,
            float scale
    ) {
        int contentLeft = Math.max(0, paddingLeft);
        int availableRight = Math.max(contentLeft, viewWidth - Math.max(0, paddingRight));
        int contentRight = contentLeft;
        boolean hasContent = false;

        int width = Math.max(0, leftDrawableWidth);
        if (width > 0) {
            contentRight += width;
            hasContent = true;
        }

        width = Math.max(0, textWidth);
        if (width > 0) {
            if (hasContent) {
                contentRight += Math.max(0, drawableGap);
            }
            contentRight += width;
            hasContent = true;
        }

        width = Math.max(0, firstRightDrawableWidth);
        if (width > 0) {
            if (hasContent) {
                contentRight += Math.max(0, drawableGap);
            }
            contentRight += width;
            hasContent = true;
        }

        width = Math.max(0, secondRightDrawableWidth);
        if (width > 0) {
            if (hasContent) {
                contentRight += Math.max(0, drawableGap);
            }
            contentRight += width;
        }

        int badgeLeft = contentRight + Math.max(0, badgeGap);
        int badgeRight = badgeLeft + badgeSize;

        if (badgeSize <= 0
                || scale <= 0
                || badgeLeft < contentLeft
                || badgeRight > availableRight) {
            return new Result(contentRight, HIDDEN, availableRight, 0);
        }

        float scaledCenterX = (contentLeft + badgeRight) * scale / 2f;
        return new Result(contentRight, badgeLeft, availableRight, scaledCenterX);
    }
}
