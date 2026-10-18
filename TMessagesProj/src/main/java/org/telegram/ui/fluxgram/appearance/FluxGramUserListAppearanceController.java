package org.telegram.ui.fluxgram.appearance;

import org.telegram.messenger.fluxgram.appearance.AppearanceProfile;
import org.telegram.messenger.fluxgram.appearance.AppearanceRepository;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_STALE_REQUESTS = 32;

    private final int account;
    private final AppearanceRepository repository;
    private final FrameScheduler frameScheduler;
    private final Runnable flushRunnable = this::flush;
    private final LinkedHashMap<FluxGramPeerKey, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Target, FluxGramPeerKey> targets = new IdentityHashMap<>();
    private final Map<FluxGramPeerKey, Integer> targetCounts = new LinkedHashMap<>();
    private final Set<Target> knownTargets = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final LinkedHashSet<FluxGramPeerKey> queuedPeers = new LinkedHashSet<>();
    private final LinkedHashSet<BatchRequest> requests = new LinkedHashSet<>();

    private long generation;
    private long lastRequestId;
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
        replaceTarget(target, supportedPeer);

        if (supportedPeer == null) {
            target.apply(null, false);
            return;
        }

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

        pruneEntries();
    }

    public void unbind(Target target) {
        if (target == null) {
            return;
        }

        FluxGramPeerKey peer = targets.remove(target);
        decrementTargetCount(peer);
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

        for (BatchRequest request : new ArrayList<>(requests)) {
            request.cancel();
        }
        requests.clear();
        queuedPeers.clear();
        entries.clear();
        targets.clear();
        targetCounts.clear();

        for (Target target : knownTargets) {
            target.destroyAppearance();
        }
        knownTargets.clear();
    }

    private void replaceTarget(Target target, FluxGramPeerKey peer) {
        FluxGramPeerKey previous = targets.get(target);
        if (peer != null && peer.equals(previous)) {
            return;
        }

        if (previous != null) {
            targets.remove(target);
            decrementTargetCount(previous);
        }
        if (peer != null) {
            targets.put(target, peer);
            targetCounts.put(peer, targetCounts.getOrDefault(peer, 0) + 1);
        }
    }

    private void decrementTargetCount(FluxGramPeerKey peer) {
        if (peer == null) {
            return;
        }

        Integer count = targetCounts.get(peer);
        if (count == null || count <= 1) {
            targetCounts.remove(peer);

            if (queuedPeers.remove(peer)) {
                entries.remove(peer);
            }
            if (queuedPeers.isEmpty() && frameScheduled) {
                frameScheduler.cancel(flushRunnable);
                frameScheduled = false;
            }
        } else {
            targetCounts.put(peer, count - 1);
        }

        pruneEntries();
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

        ArrayList<FluxGramPeerKey> peers = new ArrayList<>();
        for (FluxGramPeerKey peer : queuedPeers) {
            Entry entry = entries.get(peer);
            if (targetCounts.containsKey(peer) && entry != null && entry.state == State.PENDING) {
                peers.add(peer);
            } else if (entry != null && entry.requestId == 0) {
                entries.remove(peer);
            }
        }
        queuedPeers.clear();

        if (peers.isEmpty()) {
            return;
        }

        long requestGeneration = generation;

        for (int start = 0; start < peers.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, peers.size());
            List<FluxGramPeerKey> chunk = new ArrayList<>(peers.subList(start, end));
            long requestId = ++lastRequestId;

            for (FluxGramPeerKey peer : chunk) {
                Entry entry = entries.get(peer);
                if (entry != null) {
                    entry.requestId = requestId;
                }
            }

            BatchRequest request = new BatchRequest(requestGeneration, requestId, chunk);
            requests.add(request);
            AppearanceRepository.Subscription subscription = repository.loadBatch(
                    chunk,
                    request::onLookup
            );
            request.setSubscription(subscription);
        }
    }

    private void applyLookup(long requestGeneration, long requestId, AppearanceRepository.Lookup lookup) {
        if (destroyed
                || requestGeneration != generation
                || lookup.getAccount() != account
                || lookup.getRequestedPeer() == null) {
            return;
        }

        FluxGramPeerKey peer = lookup.getRequestedPeer();
        Entry entry = entries.get(peer);

        if (entry == null
                || entry.requestId != requestId
                || entry.state != State.PENDING && !entry.delivered) {
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

        pruneEntries();
    }

    private void pruneEntries() {
        if (entries.size() <= MAX_ENTRIES) {
            return;
        }

        Iterator<Map.Entry<FluxGramPeerKey, Entry>> iterator = entries.entrySet().iterator();
        while (entries.size() > MAX_ENTRIES && iterator.hasNext()) {
            Map.Entry<FluxGramPeerKey, Entry> candidate = iterator.next();
            FluxGramPeerKey peer = candidate.getKey();
            Entry entry = candidate.getValue();

            if (!targetCounts.containsKey(peer)
                    && !queuedPeers.contains(peer)
                    && entry.state != State.PENDING
                    && entry.requestId == 0) {
                iterator.remove();
            }
        }
    }

    private void pruneStaleRequests() {
        int staleCount = 0;
        for (BatchRequest request : requests) {
            if (request.staleDelivered) {
                staleCount++;
            }
        }
        if (staleCount <= MAX_STALE_REQUESTS) {
            return;
        }

        for (BatchRequest request : new ArrayList<>(requests)) {
            if (request.staleDelivered) {
                request.cancel();
                staleCount--;

                if (staleCount <= MAX_STALE_REQUESTS) {
                    break;
                }
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
        private long requestId;
        private boolean delivered;
    }

    private final class BatchRequest {
        private final long requestGeneration;
        private final long requestId;
        private final Set<FluxGramPeerKey> pendingPeers;

        private AppearanceRepository.Subscription subscription;
        private boolean completed;
        private boolean staleDelivered;

        private BatchRequest(long requestGeneration, long requestId, List<FluxGramPeerKey> peers) {
            this.requestGeneration = requestGeneration;
            this.requestId = requestId;
            pendingPeers = new LinkedHashSet<>(peers);
        }

        private void setSubscription(AppearanceRepository.Subscription subscription) {
            this.subscription = subscription;

            if (completed || destroyed || generation != requestGeneration) {
                subscription.cancel();
            }
        }

        private void onLookup(AppearanceRepository.Lookup lookup) {
            if (completed) {
                return;
            }

            applyLookup(requestGeneration, requestId, lookup);

            if (lookup.isStale()) {
                staleDelivered = true;
                pruneStaleRequests();
            } else {
                releaseEntry(lookup.getRequestedPeer());
                pendingPeers.remove(lookup.getRequestedPeer());
                if (pendingPeers.isEmpty()) {
                    complete();
                }
            }
        }

        private void complete() {
            if (completed) {
                return;
            }

            completed = true;
            requests.remove(this);
            releaseEntries();

            if (subscription != null) {
                subscription.cancel();
            }
        }

        private void cancel() {
            if (completed) {
                return;
            }

            completed = true;
            requests.remove(this);
            releaseEntries();

            if (subscription != null) {
                subscription.cancel();
            }
        }

        private void releaseEntries() {
            for (FluxGramPeerKey peer : pendingPeers) {
                releaseEntry(peer);
            }

            pruneEntries();
        }

        private void releaseEntry(FluxGramPeerKey peer) {
            Entry entry = entries.get(peer);
            if (entry != null && entry.requestId == requestId) {
                entry.requestId = 0;
            }
        }
    }
}
