package org.telegram.messenger.fluxgram;

public final class AvatarGeometry {
    public static final int MAX_CORNER_RADIUS = 28;

    public static final int ROLE_USER = 0;
    public static final int ROLE_GROUP = 1;
    public static final int ROLE_FORUM = 2;
    public static final int ROLE_COMMUNITY = 3;
    public static final int ROLE_MONOFORUM = 4;
    public static final int ROLE_PROFILE = 5;
    public static final int ROLE_HEADER = 6;
    public static final int ROLE_WIDGET = 7;

    public static int resolveRadius(
            int account,
            int avatarSize,
            int role,
            int stockRadius
    ) {
        return resolveRadius(
                AppearanceSettings.getAvatarCornerRadius(account),
                AppearanceSettings.usesUnifiedAvatarRadius(account),
                avatarSize,
                role,
                stockRadius
        );
    }

    public static int resolveRadius(
            int setting,
            boolean unified,
            int avatarSize,
            int role,
            int stockRadius
    ) {
        if (setting == MAX_CORNER_RADIUS && !unified) {
            return stockRadius;
        }

        float radius = avatarSize * setting / (MAX_CORNER_RADIUS * 2f);
        if (!unified) {
            if (role == ROLE_FORUM) {
                radius *= 4f / 7f;
            } else if (role == ROLE_COMMUNITY) {
                radius *= 3f / 7f;
            } else if (role == ROLE_MONOFORUM) {
                radius /= MAX_CORNER_RADIUS;
            }
        }

        return Math.max(0, Math.min(avatarSize / 2, Math.round(radius)));
    }

    public static boolean usesStockGeometry(int account) {
        return AppearanceSettings.getAvatarCornerRadius(account) == MAX_CORNER_RADIUS
                && !AppearanceSettings.usesUnifiedAvatarRadius(account);
    }

    private AvatarGeometry() {
    }
}
