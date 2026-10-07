package org.telegram.ui.fluxgram.appearance;

public final class UserListBadgePlacement {
    public static final int HIDDEN = -1;

    private UserListBadgePlacement() {
    }

    public static int calculate(
            boolean rtl,
            int contentLeft,
            int contentRight,
            int availableLeft,
            int availableRight,
            int gap,
            int badgeSize
    ) {
        int left = Math.max(0, availableLeft);
        int right = Math.max(left, availableRight);
        int size = Math.max(0, badgeSize);
        int spacing = Math.max(0, gap);

        if (size == 0 || contentLeft < left || contentRight > right || contentLeft > contentRight) {
            return HIDDEN;
        }

        int badgeLeft = rtl
                ? contentLeft - spacing - size
                : contentRight + spacing;

        if (badgeLeft < left || badgeLeft + size > right) {
            return HIDDEN;
        }

        return badgeLeft;
    }
}
