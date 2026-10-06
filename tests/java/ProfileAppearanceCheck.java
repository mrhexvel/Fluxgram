import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.messenger.fluxgram.appearance.ProfileBadgeCatalog;

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
                5373141891321699086L,
                "Developer",
                "Description",
                true
        );
        expect(badge != null, "allowlisted badge");
        expect(ProfileBadgeCatalog.resolve(
                "unknown",
                5373141891321699086L,
                "Developer",
                "Description",
                true
        ) == null, "unknown badge");
        expect(ProfileBadgeCatalog.resolve(
                "developer",
                1,
                "Developer",
                "Description",
                true
        ) == null, "unexpected document");
        expect(ProfileBadgeCatalog.resolve(
                "developer",
                5373141891321699086L,
                "Developer",
                "Description",
                false
        ) == null, "particles required");
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }
}
