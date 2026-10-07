package org.telegram.messenger.fluxgram.appearance;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppearanceProfile {
    private final FluxGramPeerKey peer;
    private final BigInteger revision;
    private final long expiresAt;
    private final List<ProfileBadge> badges;
    private final AppearanceApiDto.Design design;

    private AppearanceProfile(
            FluxGramPeerKey peer,
            BigInteger revision,
            long expiresAt,
            List<ProfileBadge> badges,
            AppearanceApiDto.Design design
    ) {
        this.peer = peer;
        this.revision = revision;
        this.expiresAt = expiresAt;
        this.badges = Collections.unmodifiableList(badges);
        this.design = design;
    }

    public static AppearanceProfile fromApi(AppearanceApiDto.Result result, long now) {
        if (result.getProfile() == null) {
            return null;
        }

        ArrayList<ProfileBadge> badges = new ArrayList<>();

        for (AppearanceApiDto.Badge value : result.getProfile().getBadges()) {
            if (value.getExpiresAt() != null && value.getExpiresAt() <= now) {
                continue;
            }

            ProfileBadge badge = ProfileBadgeCatalog.resolve(
                    value.getId(),
                    value.getDocumentId(),
                    value.getTitle(),
                    value.getDescription(),
                    value.hasParticles()
            );

            if (badge != null) {
                badges.add(badge);
            }
        }

        return new AppearanceProfile(
                result.getPeer(),
                result.getRevision(),
                result.getExpiresAt(),
                badges,
                result.getProfile().getDesign()
        );
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

    public List<ProfileBadge> getBadges() {
        return badges;
    }

    public AppearanceApiDto.Design getDesign() {
        return design;
    }
}
