package org.telegram.ui.fluxgram.appearance;

import org.telegram.messenger.fluxgram.appearance.AppearanceProfile;
import org.telegram.messenger.fluxgram.appearance.AppearanceRepository;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FluxGramUserListAppearanceController {
    public interface FrameScheduler {
        void post(Runnable runnable);

        void cancel(Runnable runnable);
    }

    public interface Target {
        FluxGramPeerKey getRequestedPeer();

        void apply(ProfileBadge badge, boolean animated);

        void destroyAppearance();
    }

    private static final int MAX_BATCH_SIZE = 100;

    private final int account;
    private final AppearanceRepository repository;
    private final FrameScheduler frameScheduler;
    private final Runnable flushRunnable = this::flush;
    private final Map<FluxGramPeerKey, Entry> entries = new HashMap<>();
    private final Map<Target, FluxGramPeerKey> targets = new IdentityHashMap<>();
    private final Set<Target> knownTargets = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final LinkedHashSet<FluxGramPeerKey> queuedPeers = new LinkedHashSet<>();
    private final ArrayList<AppearanceRepository.Subscription> subscriptions = new ArrayList<>();

    private long generation;
    private boolean frameScheduled;
    private boolean destroyed;

    public FluxGramUserListAppearanceController(
            int account,
            AppearanceRepository repository,
            FrameScheduler frameScheduler
    ) {
        if (repository == null || frameScheduler == null) {
            throw new IllegalArgumentException("repository and frameScheduler must not be null");
        }

        this.account = account;
        this.repository = repository;
        this.frameScheduler = frameScheduler;
    }

    public void bind(Target target, FluxGramPeerKey peer) {
        if (destroyed || target == null) {
            return;
        }

        knownTargets.add(target);
        FluxGramPeerKey supportedPeer = isSupported(peer) ? peer : null;

        if (supportedPeer == null) {
            targets.remove(target);
            target.apply(null, false);
            return;
        }

        targets.put(target, supportedPeer);
        Entry entry = entries.get(supportedPeer);

        if (entry == null) {
            entry = new Entry();
            entries.put(supportedPeer, entry);
            queuedPeers.add(supportedPeer);
            scheduleFlush();
        }

        if (entry.state == State.PROFILE) {
            target.apply(entry.badge, false);
        } else {
            target.apply(null, false);
        }
    }

    public void unbind(Target target) {
        if (target != null) {
            targets.remove(target);
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        destroyed = true;
        generation++;

        if (frameScheduled) {
            frameScheduler.cancel(flushRunnable);
            frameScheduled = false;
        }

        for (AppearanceRepository.Subscription subscription : subscriptions) {
            subscription.cancel();
        }
        subscriptions.clear();
        queuedPeers.clear();
        entries.clear();
        targets.clear();

        for (Target target : knownTargets) {
            target.destroyAppearance();
        }
        knownTargets.clear();
    }

    private void scheduleFlush() {
        if (frameScheduled || destroyed) {
            return;
        }

        frameScheduled = true;
        frameScheduler.post(flushRunnable);
    }

    private void flush() {
        frameScheduled = false;

        if (destroyed || queuedPeers.isEmpty()) {
            return;
        }

        ArrayList<FluxGramPeerKey> peers = new ArrayList<>(queuedPeers);
        queuedPeers.clear();
        long requestGeneration = generation;

        for (int start = 0; start < peers.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, peers.size());
            List<FluxGramPeerKey> chunk = new ArrayList<>(peers.subList(start, end));
            AppearanceRepository.Subscription subscription = repository.loadBatch(
                    chunk,
                    lookup -> applyLookup(requestGeneration, lookup)
            );

            if (destroyed || generation != requestGeneration) {
                subscription.cancel();
            } else {
                subscriptions.add(subscription);
            }
        }
    }

    private void applyLookup(long requestGeneration, AppearanceRepository.Lookup lookup) {
        if (destroyed
                || requestGeneration != generation
                || lookup.getAccount() != account
                || lookup.getRequestedPeer() == null) {
            return;
        }

        FluxGramPeerKey peer = lookup.getRequestedPeer();
        Entry entry = entries.get(peer);

        if (entry == null || entry.state != State.PENDING && !entry.delivered) {
            return;
        }

        ProfileBadge badge = firstBadge(lookup);
        if (lookup.getStatus() == AppearanceRepository.Lookup.Status.PROFILE) {
            entry.state = badge == null ? State.NONE : State.PROFILE;
            entry.badge = badge;
        } else if (lookup.getStatus() == AppearanceRepository.Lookup.Status.NONE) {
            entry.state = State.NONE;
            entry.badge = null;
        } else {
            if (!entry.delivered) {
                entries.remove(peer);
            }
            return;
        }

        boolean animated = entry.delivered;
        entry.delivered = true;

        for (Map.Entry<Target, FluxGramPeerKey> targetEntry : targets.entrySet()) {
            Target target = targetEntry.getKey();
            if (peer.equals(targetEntry.getValue()) && peer.equals(target.getRequestedPeer())) {
                target.apply(entry.badge, animated);
            }
        }
    }

    private static ProfileBadge firstBadge(AppearanceRepository.Lookup lookup) {
        AppearanceProfile profile = lookup.getProfile();
        if (profile == null) {
            return null;
        }

        List<ProfileBadge> badges = profile.getBadges();
        return badges.isEmpty() ? null : badges.get(0);
    }

    private static boolean isSupported(FluxGramPeerKey peer) {
        return peer != null && peer.getType() == FluxGramPeerKey.Type.USER;
    }

    private enum State {
        PENDING,
        PROFILE,
        NONE
    }

    private static final class Entry {
        private State state = State.PENDING;
        private ProfileBadge badge;
        private boolean delivered;
    }
}
