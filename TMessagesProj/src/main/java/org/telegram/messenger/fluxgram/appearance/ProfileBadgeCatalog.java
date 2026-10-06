package org.telegram.messenger.fluxgram.appearance;

public final class ProfileBadgeCatalog {
    private static final String DEVELOPER_ID = "developer";
    private static final long DEVELOPER_DOCUMENT_ID = 5373141891321699086L;

    private ProfileBadgeCatalog() {
    }

    public static ProfileBadge resolve(
            String id,
            long documentId,
            String title,
            String description,
            boolean particles
    ) {
        if (!DEVELOPER_ID.equals(id) || documentId != DEVELOPER_DOCUMENT_ID || !particles) {
            return null;
        }
        if (title == null || title.isEmpty() || description == null || description.isEmpty()) {
            return null;
        }

        return new ProfileBadge(id, documentId, title, description);
    }
}
