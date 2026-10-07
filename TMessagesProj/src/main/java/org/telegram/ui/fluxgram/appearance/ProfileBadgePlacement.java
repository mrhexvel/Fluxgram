package org.telegram.ui.fluxgram.appearance;

public final class ProfileBadgePlacement {
    public static final int HIDDEN = -1;

    private ProfileBadgePlacement() {
    }

    public static int findLeft(
            int textRight,
            int firstOfficialStatusRight,
            int secondOfficialStatusRight,
            int availableRight,
            int gap,
            int badgeSize
    ) {
        int officialStatusRight = Math.max(firstOfficialStatusRight, secondOfficialStatusRight);
        int contentRight = Math.max(textRight, officialStatusRight);
        int badgeLeft = contentRight + gap;

        if (badgeLeft < 0 || badgeSize <= 0 || badgeLeft + badgeSize > availableRight) {
            return HIDDEN;
        }

        return badgeLeft;
    }
}
