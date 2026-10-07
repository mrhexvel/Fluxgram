package org.telegram.messenger.fluxgram.appearance;

import java.util.Objects;

public final class ProfileBadge {
    private final String id;
    private final long documentId;
    private final String title;
    private final String description;
    private final boolean particles;

    public ProfileBadge(
            String id,
            long documentId,
            String title,
            String description,
            boolean particles
    ) {
        this.id = id;
        this.documentId = documentId;
        this.title = title;
        this.description = description;
        this.particles = particles;
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

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ProfileBadge)) {
            return false;
        }

        ProfileBadge badge = (ProfileBadge) object;
        return documentId == badge.documentId
                && particles == badge.particles
                && id.equals(badge.id)
                && title.equals(badge.title)
                && description.equals(badge.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, documentId, title, description, particles);
    }
}
