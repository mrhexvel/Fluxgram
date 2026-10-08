import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.messenger.fluxgram.appearance.ProfileBadgeCatalog;
import org.telegram.ui.fluxgram.appearance.ProfileBadgePlacement;

import java.util.HashSet;

public final class ProfileAppearanceCheck {
    public static void main(String[] args) {
        FluxGramPeerKey user = new FluxGramPeerKey(FluxGramPeerKey.Type.USER, 100);
        FluxGramPeerKey group = new FluxGramPeerKey(FluxGramPeerKey.Type.GROUP, 100);
        FluxGramPeerKey channel = new FluxGramPeerKey(FluxGramPeerKey.Type.CHANNEL, 100);
        HashSet<FluxGramPeerKey> keys = new HashSet<>();
        keys.add(user);
        keys.add(group);
        keys.add(channel);

        expect(keys.size() == 3, "peer type namespace");
        expect(FluxGramPeerKey.Type.fromApiValue("user") == FluxGramPeerKey.Type.USER, "user type");
        expect(FluxGramPeerKey.Type.fromApiValue("community") == null, "unknown type");

        ProfileBadge badge = ProfileBadgeCatalog.resolve(
                "developer",
                5350701541898628901L,
                "Developer",
                "Description",
                true
        );
        expect(badge != null, "backend badge");
        expect(ProfileBadgeCatalog.resolve(
                "unknown",
                5350701541898628901L,
                "Developer",
                "Description",
                true
        ) != null, "backend-defined badge");
        expect(ProfileBadgeCatalog.resolve(
                "developer",
                0,
                "Developer",
                "Description",
                true
        ) == null, "invalid document");
        expect(ProfileBadgeCatalog.resolve(
                "developer",
                5350701541898628901L,
                "Developer",
                "",
                false
        ) != null, "particles and empty description are supported");
        ProfileBadge withoutParticles = ProfileBadgeCatalog.resolve(
                "developer",
                5350701541898628901L,
                "Developer",
                "Description",
                false
        );
        expect(!withoutParticles.hasParticles(), "particles value retained");
        expect(!badge.equals(withoutParticles), "particles participate in equality");

        ProfileBadgePlacement.Result shortName = placement(80, 0, 0, 0, 0, 0, 160, 1f);
        expect(shortName.isVisible() && shortName.getBadgeLeft() == 88,
                "badge after short name");
        expect(shortName.getScaledCenterX() == 54f,
                "name and badge expose their common center");
        expect(160 / 2f - shortName.getScaledCenterX() == 26f,
                "centered group has symmetric screen margins");

        ProfileBadgePlacement.Result officialStatuses = placement(80, 20, 16, 18, 0, 0, 200, 1f);
        expect(officialStatuses.isVisible() && officialStatuses.getBadgeLeft() == 154,
                "badge follows left verification, text, and both official statuses");
        expect(officialStatuses.getContentRight() == 146,
                "official status cluster remains before badge");
        expect(200 / 2f - officialStatuses.getScaledCenterX() == 13f,
                "full status group remains centered");

        ProfileBadgePlacement.Result boundary = placement(80, 0, 0, 0, 0, 0, 108, 1f);
        expect(boundary.isVisible() && boundary.getBadgeLeft() == 88,
                "badge fits exact boundary");
        expect(!placement(80, 0, 0, 0, 0, 0, 107, 1f).isVisible(),
                "badge hides beyond boundary");
        expect(!placement(140, 0, 0, 0, 0, 0, 160, 1f).isVisible(),
                "long name has priority");

        ProfileBadgePlacement.Result paddedScaled = placement(60, 18, 16, 20, 6, 10, 220, 1.25f);
        expect(paddedScaled.isVisible() && paddedScaled.getBadgeLeft() == 140,
                "padding and drawable gaps participate in placement");
        expect(paddedScaled.getAvailableRight() == 210,
                "right padding limits visible bounds");
        expect(paddedScaled.getScaledCenterX() == 103.75f,
                "view scale participates in center calculation");
        float scaledTranslation = 220 / 2f - paddedScaled.getScaledCenterX();
        expect(scaledTranslation + 6 * 1.25f == 13.75f
                        && scaledTranslation + 160 * 1.25f == 206.25f,
                "scaled group has symmetric screen margins");
    }

    private static ProfileBadgePlacement.Result placement(
            int textWidth,
            int leftDrawableWidth,
            int firstRightDrawableWidth,
            int secondRightDrawableWidth,
            int paddingLeft,
            int paddingRight,
            int viewWidth,
            float scale
    ) {
        return ProfileBadgePlacement.calculate(
                textWidth,
                leftDrawableWidth,
                firstRightDrawableWidth,
                secondRightDrawableWidth,
                4,
                8,
                20,
                paddingLeft,
                paddingRight,
                viewWidth,
                scale
        );
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }
}
