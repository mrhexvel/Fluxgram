package org.telegram.messenger.fluxgram.appearance;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public final class AppearanceApiDto {
    public static final class Badge {
        private final String id;
        private final long documentId;
        private final String title;
        private final String description;
        private final boolean particles;
        private final Long expiresAt;

        public Badge(String id, long documentId, String title, String description, boolean particles, Long expiresAt) {
            this.id = id;
            this.documentId = documentId;
            this.title = title;
            this.description = description;
            this.particles = particles;
            this.expiresAt = expiresAt;
        }

        public String getId() {
            return id;
        }

        public long getDocumentId() {
            return documentId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public boolean hasParticles() {
            return particles;
        }

        public Long getExpiresAt() {
            return expiresAt;
        }
    }

    public static final class Design {
        private final String designId;
        private final String paletteId;
        private final String patternId;
        private final String avatarFrameId;
        private final String badgeStyleId;

        public Design(String designId, String paletteId, String patternId, String avatarFrameId, String badgeStyleId) {
            this.designId = designId;
            this.paletteId = paletteId;
            this.patternId = patternId;
            this.avatarFrameId = avatarFrameId;
            this.badgeStyleId = badgeStyleId;
        }

        public String getDesignId() {
            return designId;
        }

        public String getPaletteId() {
            return paletteId;
        }

        public String getPatternId() {
            return patternId;
        }

        public String getAvatarFrameId() {
            return avatarFrameId;
        }

        public String getBadgeStyleId() {
            return badgeStyleId;
        }
    }

    public static final class Profile {
        private final List<Badge> badges;
        private final Design design;

        public Profile(List<Badge> badges, Design design) {
            this.badges = Collections.unmodifiableList(badges);
            this.design = design;
        }

        public List<Badge> getBadges() {
            return badges;
        }

        public Design getDesign() {
            return design;
        }
    }

    public static final class Result {
        private final FluxGramPeerKey peer;
        private final BigInteger revision;
        private final long expiresAt;
        private final Profile profile;
        private final String json;

        public Result(FluxGramPeerKey peer, BigInteger revision, long expiresAt, Profile profile, String json) {
            this.peer = peer;
            this.revision = revision;
            this.expiresAt = expiresAt;
            this.profile = profile;
            this.json = json;
        }

        public FluxGramPeerKey getPeer() {
            return peer;
        }

        public BigInteger getRevision() {
            return revision;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public Profile getProfile() {
            return profile;
        }

        public String getJson() {
            return json;
        }
    }

    private AppearanceApiDto() {
    }
}
