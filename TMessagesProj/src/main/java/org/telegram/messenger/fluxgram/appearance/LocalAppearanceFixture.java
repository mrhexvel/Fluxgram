package org.telegram.messenger.fluxgram.appearance;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class LocalAppearanceFixture {
    private static final String ASSET_PATH = "fluxgram/appearance/v1/profile-found.json";
    private static final int SCHEMA_VERSION = 1;
    private static final int RENDERER_VERSION = 1;

    private final FluxGramPeerKey peer;
    private final ProfileBadge badge;

    private LocalAppearanceFixture(FluxGramPeerKey peer, ProfileBadge badge) {
        this.peer = peer;
        this.badge = badge;
    }

    public static LocalAppearanceFixture load(Context context) {
        try (
                InputStream stream = context.getAssets().open(ASSET_PATH);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)
                )
        ) {
            StringBuilder contents = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                contents.append(line);
            }

            return parse(new JSONObject(contents.toString()));
        } catch (Exception error) {
            FileLog.e(error);
            return empty();
        }
    }

    public ProfileBadge findBadge(FluxGramPeerKey key) {
        if (key == null || !key.equals(peer)) {
            return null;
        }

        return badge;
    }

    private static LocalAppearanceFixture parse(JSONObject root) throws Exception {
        if (root.optInt("schema_version", -1) != SCHEMA_VERSION) {
            return empty();
        }
        if (root.optInt("minimum_renderer_version", Integer.MAX_VALUE) > RENDERER_VERSION) {
            return empty();
        }

        JSONObject result = root.getJSONObject("result");
        JSONObject peerObject = result.getJSONObject("peer");
        FluxGramPeerKey.Type type = FluxGramPeerKey.Type.fromApiValue(
                peerObject.getString("peer_type")
        );

        if (type == null) {
            return empty();
        }

        long peerId = Long.parseLong(peerObject.getString("peer_id"));
        FluxGramPeerKey peer = new FluxGramPeerKey(type, peerId);
        JSONObject profile = result.optJSONObject("profile");

        if (profile == null) {
            return empty();
        }

        JSONArray badges = profile.optJSONArray("badges");
        if (badges == null || badges.length() == 0) {
            return empty();
        }

        JSONObject badgeObject = badges.getJSONObject(0);
        ProfileBadge badge = ProfileBadgeCatalog.resolve(
                badgeObject.getString("badge_id"),
                Long.parseLong(badgeObject.getString("custom_emoji_document_id")),
                badgeObject.getString("title"),
                badgeObject.getString("description"),
                badgeObject.optBoolean("particles", false)
        );

        if (badge == null) {
            return empty();
        }

        return new LocalAppearanceFixture(peer, badge);
    }

    private static LocalAppearanceFixture empty() {
        return new LocalAppearanceFixture(null, null);
    }
}
