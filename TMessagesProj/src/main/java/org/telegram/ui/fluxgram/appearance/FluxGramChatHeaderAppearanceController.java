package org.telegram.ui.fluxgram.appearance;

import org.telegram.messenger.fluxgram.appearance.AppearanceProfile;
import org.telegram.messenger.fluxgram.appearance.AppearanceRepository;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;

import java.util.List;

public final class FluxGramChatHeaderAppearanceController {
    public interface Target {
        void apply(ProfileBadge badge, boolean animated);
    }

    private final int account;
    private final AppearanceRepository repository;
    private final Target target;

    private AppearanceRepository.Subscription subscription;
    private FluxGramPeerKey requestedPeer;
    private ProfileBadge cachedBadge;
    private long generation;
    private boolean delivered;
    private boolean destroyed;

    public FluxGramChatHeaderAppearanceController(
            int account,
            AppearanceRepository repository,
            Target target
    ) {
        if (repository == null || target == null) {
            throw new IllegalArgumentException("repository and target must not be null");
        }

        this.account = account;
        this.repository = repository;
        this.target = target;
    }

    public void bind(FluxGramPeerKey peer) {
        if (destroyed) {
            return;
        }

        FluxGramPeerKey supportedPeer = isSupported(peer) ? peer : null;
        if (samePeer(requestedPeer, supportedPeer)) {
            target.apply(cachedBadge, false);
            return;
        }

        generation++;
        cancelSubscription();
        requestedPeer = supportedPeer;
        cachedBadge = null;
        delivered = false;
        target.apply(null, false);

        if (supportedPeer == null) {
            return;
        }

        long requestGeneration = generation;
        AppearanceRepository.Subscription nextSubscription = repository.load(
                supportedPeer,
                lookup -> applyLookup(supportedPeer, requestGeneration, lookup)
        );

        if (destroyed || generation != requestGeneration || !supportedPeer.equals(requestedPeer)) {
            nextSubscription.cancel();
        } else {
            subscription = nextSubscription;
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        destroyed = true;
        generation++;
        cancelSubscription();
        requestedPeer = null;
        cachedBadge = null;
        delivered = false;
        target.apply(null, false);
    }

    private void applyLookup(
            FluxGramPeerKey peer,
            long requestGeneration,
            AppearanceRepository.Lookup lookup
    ) {
        if (destroyed
                || requestGeneration != generation
                || lookup.getAccount() != account
                || !peer.equals(requestedPeer)
                || !peer.equals(lookup.getRequestedPeer())) {
            return;
        }

        ProfileBadge badge = null;
        AppearanceProfile profile = lookup.getProfile();

        if (lookup.getStatus() == AppearanceRepository.Lookup.Status.PROFILE && profile != null) {
            List<ProfileBadge> badges = profile.getBadges();
            if (!badges.isEmpty()) {
                badge = badges.get(0);
            }
        }

        boolean animated = delivered;
        delivered = true;
        cachedBadge = badge;
        target.apply(badge, animated);
    }

    private void cancelSubscription() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    private static boolean isSupported(FluxGramPeerKey peer) {
        return peer != null && peer.getType() == FluxGramPeerKey.Type.USER;
    }

    private static boolean samePeer(FluxGramPeerKey first, FluxGramPeerKey second) {
        return first == null ? second == null : first.equals(second);
    }
}
