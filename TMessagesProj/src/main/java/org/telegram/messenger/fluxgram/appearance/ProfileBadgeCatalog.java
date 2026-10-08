package org.telegram.messenger.fluxgram.appearance;

public final class ProfileBadgeCatalog {
    private ProfileBadgeCatalog() {
    }

    public static ProfileBadge resolve(
            String id,
            long documentId,
            String title,
            String description,
            boolean particles
    ) {
        if (id == null || id.isEmpty() || documentId <= 0
                || title == null || title.isEmpty() || description == null) {
            return null;
        }

        return new ProfileBadge(id, documentId, title, description, particles);
    }
}
