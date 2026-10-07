package org.telegram.messenger.fluxgram.appearance;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class AppearanceApiParser {
    public static final int SCHEMA_VERSION = 1;
    public static final int RENDERER_VERSION = 1;
    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    public AppearanceApiDto.Result parseSingle(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        validateEnvelope(root);
        return parseResult(root.getJSONObject("result"));
    }

    public List<AppearanceApiDto.Result> parseBatch(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        validateEnvelope(root);
        JSONArray values = root.getJSONArray("results");

        if (values.length() < 1 || values.length() > 100) {
            throw new IllegalArgumentException("invalid result count");
        }

        ArrayList<AppearanceApiDto.Result> results = new ArrayList<>(values.length());

        for (int index = 0; index < values.length(); index++) {
            results.add(parseResult(values.getJSONObject(index)));
        }

        return results;
    }

    public AppearanceApiDto.Result parseResult(String json) throws Exception {
        return parseResult(new JSONObject(json));
    }

    public String createBatchRequest(List<FluxGramPeerKey> peers) throws Exception {
        if (peers.isEmpty() || peers.size() > 100) {
            throw new IllegalArgumentException("batch size must be between 1 and 100");
        }

        JSONArray values = new JSONArray();

        for (FluxGramPeerKey peer : peers) {
            values.put(peerJson(peer));
        }

        return new JSONObject().put("peers", values).toString();
    }

    private void validateEnvelope(JSONObject root) throws Exception {
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported schema version");
        }

        int rendererVersion = root.getInt("minimum_renderer_version");

        if (rendererVersion < 1 || rendererVersion > RENDERER_VERSION) {
            throw new IllegalArgumentException("unsupported renderer version");
        }
    }

    private AppearanceApiDto.Result parseResult(JSONObject value) throws Exception {
        FluxGramPeerKey peer = parsePeer(value.getJSONObject("peer"));
        String revisionValue = value.getString("revision");
        BigInteger revision = new BigInteger(revisionValue);

        if (revision.signum() < 0 || !revision.toString().equals(revisionValue)) {
            throw new IllegalArgumentException("invalid revision");
        }

        long expiresAt = Instant.parse(value.getString("expires_at")).toEpochMilli();
        AppearanceApiDto.Profile profile = value.isNull("profile")
                ? null
                : parseProfile(value.getJSONObject("profile"));

        return new AppearanceApiDto.Result(peer, revision, expiresAt, profile, value.toString());
    }

    private FluxGramPeerKey parsePeer(JSONObject value) throws Exception {
        FluxGramPeerKey.Type type = FluxGramPeerKey.Type.fromApiValue(value.getString("peer_type"));

        if (type == null) {
            throw new IllegalArgumentException("invalid peer type");
        }

        String idValue = value.getString("peer_id");

        if (!idValue.matches("[1-9][0-9]{0,15}")) {
            throw new IllegalArgumentException("invalid peer id");
        }

        long id = Long.parseLong(idValue);

        if (id > 4503599627370495L) {
            throw new IllegalArgumentException("peer id exceeds contract range");
        }

        return new FluxGramPeerKey(type, id);
    }

    private AppearanceApiDto.Profile parseProfile(JSONObject value) throws Exception {
        JSONArray badgeValues = value.getJSONArray("badges");

        if (badgeValues.length() > 8) {
            throw new IllegalArgumentException("too many badges");
        }

        ArrayList<AppearanceApiDto.Badge> badges = new ArrayList<>(badgeValues.length());

        for (int index = 0; index < badgeValues.length(); index++) {
            badges.add(parseBadge(badgeValues.getJSONObject(index)));
        }

        AppearanceApiDto.Design design = value.isNull("design")
                ? null
                : parseDesign(value.getJSONObject("design"));

        if (badges.isEmpty() && design == null) {
            throw new IllegalArgumentException("empty profile");
        }

        return new AppearanceApiDto.Profile(badges, design);
    }

    private AppearanceApiDto.Badge parseBadge(JSONObject value) throws Exception {
        String id = stableId(value.getString("badge_id"));
        String documentValue = value.getString("custom_emoji_document_id");
        BigInteger documentId = new BigInteger(documentValue);

        if (documentId.signum() <= 0 || documentId.bitLength() > 63 || !documentId.toString().equals(documentValue)) {
            throw new IllegalArgumentException("invalid document id");
        }

        String title = text(value.getString("title"), 1, 64);
        String description = text(value.getString("description"), 0, 256);
        Long expiresAt = value.isNull("expires_at")
                ? null
                : Instant.parse(value.getString("expires_at")).toEpochMilli();

        return new AppearanceApiDto.Badge(
                id,
                documentId.longValue(),
                title,
                description,
                value.getBoolean("particles"),
                expiresAt
        );
    }

    private AppearanceApiDto.Design parseDesign(JSONObject value) throws Exception {
        String designId = optionalStableId(value, "design_id");
        String paletteId = optionalStableId(value, "palette_id");
        String patternId = optionalStableId(value, "pattern_id");
        String avatarFrameId = optionalStableId(value, "avatar_frame_id");
        String badgeStyleId = optionalStableId(value, "badge_style_id");

        if (designId == null && paletteId == null && patternId == null
                && avatarFrameId == null && badgeStyleId == null) {
            throw new IllegalArgumentException("empty design");
        }

        return new AppearanceApiDto.Design(
                designId,
                paletteId,
                patternId,
                avatarFrameId,
                badgeStyleId
        );
    }

    private String optionalStableId(JSONObject value, String key) throws Exception {
        return value.has(key) && !value.isNull(key) ? stableId(value.getString(key)) : null;
    }

    private String stableId(String value) {
        if (!STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid stable id");
        }

        return value;
    }

    private String text(String value, int minimum, int maximum) {
        if (value.length() < minimum || value.length() > maximum) {
            throw new IllegalArgumentException("invalid text length");
        }

        return value;
    }

    private JSONObject peerJson(FluxGramPeerKey peer) throws Exception {
        return new JSONObject()
                .put("peer_type", peer.getType().getApiValue())
                .put("peer_id", Long.toString(peer.getId()));
    }
}
