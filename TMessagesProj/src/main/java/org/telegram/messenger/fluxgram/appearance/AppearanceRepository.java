package org.telegram.messenger.fluxgram.appearance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AppearanceRepository {
    public interface Store {
        AppearanceApiDto.Result read(FluxGramPeerKey requested) throws Exception;

        AppearanceApiDto.Result readCanonical(FluxGramPeerKey canonical) throws Exception;

        void write(FluxGramPeerKey requested, AppearanceApiDto.Result result, long storedAt) throws Exception;

        void clear() throws Exception;
    }

    public interface Network {
        boolean isEnabled();

        List<AppearanceApiDto.Result> fetch(List<FluxGramPeerKey> peers) throws Exception;
    }

    public interface OwnerProvider {
        long getOwner(int account);
    }

    public interface Clock {
        long now();
    }

    public interface Callback {
        void onResult(Lookup lookup);
    }

    public static final class Lookup {
        public enum Status {
            PROFILE,
            NONE,
            UNAVAILABLE
        }

        private final int account;
        private final FluxGramPeerKey requestedPeer;
        private final FluxGramPeerKey canonicalPeer;
        private final AppearanceProfile profile;
        private final Status status;
        private final boolean stale;

        private Lookup(
                int account,
                FluxGramPeerKey requestedPeer,
                FluxGramPeerKey canonicalPeer,
                AppearanceProfile profile,
                Status status,
                boolean stale
        ) {
            this.account = account;
            this.requestedPeer = requestedPeer;
            this.canonicalPeer = canonicalPeer;
            this.profile = profile;
            this.status = status;
            this.stale = stale;
        }

        public int getAccount() {
            return account;
        }

        public FluxGramPeerKey getRequestedPeer() {
            return requestedPeer;
        }

        public FluxGramPeerKey getCanonicalPeer() {
            return canonicalPeer;
        }

        public AppearanceProfile getProfile() {
            return profile;
        }

        public Status getStatus() {
            return status;
        }

        public boolean isStale() {
            return stale;
        }
    }

    public static final class Subscription {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static final int MAX_BATCH_SIZE = 100;

    private final int account;
    private final long owner;
    private final Store store;
    private final Network network;
    private final OwnerProvider ownerProvider;
    private final Clock clock;
    private final Executor workerExecutor;
    private final Executor networkExecutor;
    private final Executor callbackExecutor;
    private final AppearanceCachePolicy cachePolicy;
    private final AppearanceMemoryCache memoryCache;
    private final AtomicLong generation = new AtomicLong();
    private final Map<FluxGramPeerKey, ArrayList<Pending>> inFlight = new HashMap<>();

    public AppearanceRepository(
            int account,
            long owner,
            Store store,
            Network network,
            OwnerProvider ownerProvider,
            Clock clock,
            Executor workerExecutor,
            Executor networkExecutor,
            Executor callbackExecutor
    ) {
        this.account = account;
        this.owner = owner;
        this.store = store;
        this.network = network;
        this.ownerProvider = ownerProvider;
        this.clock = clock;
        this.workerExecutor = workerExecutor;
        this.networkExecutor = networkExecutor;
        this.callbackExecutor = callbackExecutor;
        this.cachePolicy = new AppearanceCachePolicy();
        this.memoryCache = new AppearanceMemoryCache();
    }

    public Subscription load(FluxGramPeerKey peer, Callback callback) {
        return loadBatch(Collections.singletonList(peer), callback);
    }

    public Subscription loadBatch(Collection<FluxGramPeerKey> peers, Callback callback) {
        if (peers == null || callback == null) {
            throw new IllegalArgumentException("peers and callback must not be null");
        }

        LinkedHashSet<FluxGramPeerKey> unique = new LinkedHashSet<>(peers);

        if (unique.isEmpty()) {
            throw new IllegalArgumentException("peers must not be empty");
        }

        for (FluxGramPeerKey peer : unique) {
            if (peer == null) {
                throw new IllegalArgumentException("peer must not be null");
            }
        }

        Subscription subscription = new Subscription();
        long requestGeneration = generation.get();
        workerExecutor.execute(() -> loadOnWorker(
                new ArrayList<>(unique),
                callback,
                subscription,
                requestGeneration
        ));
        return subscription;
    }

    public void invalidate() {
        generation.incrementAndGet();
        workerExecutor.execute(() -> {
            inFlight.clear();
            memoryCache.clear();
        });
    }

    public void clear() {
        generation.incrementAndGet();
        workerExecutor.execute(() -> {
            inFlight.clear();
            memoryCache.clear();

            try {
                store.clear();
            } catch (Exception ignore) {
            }
        });
    }

    private void loadOnWorker(
            List<FluxGramPeerKey> peers,
            Callback callback,
            Subscription subscription,
            long requestGeneration
    ) {
        if (!active(subscription, requestGeneration)) {
            return;
        }

        ArrayList<FluxGramPeerKey> fetch = new ArrayList<>();

        for (FluxGramPeerKey peer : peers) {
            AppearanceApiDto.Result cached = memoryCache.get(peer);

            if (cached == null) {
                try {
                    cached = store.read(peer);

                    if (cached != null) {
                        memoryCache.put(peer, cached);
                    }
                } catch (Exception ignore) {
                    cached = null;
                }
            }

            boolean delivered = false;

            if (cached != null) {
                AppearanceCachePolicy.State state = cachePolicy.state(cached, clock.now());

                if (state == AppearanceCachePolicy.State.FRESH) {
                    dispatch(peer, cached, false, callback, subscription, requestGeneration);
                    continue;
                }
                if (state == AppearanceCachePolicy.State.STALE) {
                    dispatch(peer, cached, true, callback, subscription, requestGeneration);
                    delivered = true;
                }
            }

            Pending pending = new Pending(callback, subscription, requestGeneration, delivered);
            ArrayList<Pending> waiters = inFlight.get(peer);

            if (waiters == null) {
                waiters = new ArrayList<>();
                inFlight.put(peer, waiters);
                fetch.add(peer);
            }

            waiters.add(pending);
        }

        if (fetch.isEmpty()) {
            return;
        }
        if (!network.isEnabled()) {
            fail(fetch);
            return;
        }

        for (int start = 0; start < fetch.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, fetch.size());
            startNetwork(new ArrayList<>(fetch.subList(start, end)), requestGeneration);
        }
    }

    private void startNetwork(List<FluxGramPeerKey> peers, long requestGeneration) {
        networkExecutor.execute(() -> {
            List<AppearanceApiDto.Result> results = null;

            try {
                results = network.fetch(peers);
            } catch (Exception ignore) {
            }

            List<AppearanceApiDto.Result> completed = results;
            workerExecutor.execute(() -> completeNetwork(peers, completed, requestGeneration));
        });
    }

    private void completeNetwork(
            List<FluxGramPeerKey> requested,
            List<AppearanceApiDto.Result> results,
        long requestGeneration
    ) {
        if (requestGeneration != generation.get() || ownerProvider.getOwner(account) != owner) {
            removeInFlight(requested, requestGeneration);
            return;
        }
        if (results == null || results.size() != requested.size()) {
            fail(requested);
            return;
        }

        for (int index = 0; index < requested.size(); index++) {
            FluxGramPeerKey peer = requested.get(index);
            AppearanceApiDto.Result received = results.get(index);

            if (!validResponsePeer(peer, received.getPeer())) {
                fail(Collections.singletonList(peer));
                continue;
            }

            AppearanceApiDto.Result selected = received;
            AppearanceApiDto.Result existing = memoryCache.getCanonical(received.getPeer());
            boolean selectedStale = false;
            boolean selectedUsable = true;
            boolean retainedExisting = false;

            if (existing == null) {
                try {
                    existing = store.readCanonical(received.getPeer());
                } catch (Exception ignore) {
                    existing = null;
                }
            }

            if (existing != null && existing.getRevision().compareTo(received.getRevision()) > 0) {
                selected = existing;
                retainedExisting = true;
                AppearanceCachePolicy.State state = cachePolicy.state(existing, clock.now());
                selectedStale = state == AppearanceCachePolicy.State.STALE;
                selectedUsable = state != AppearanceCachePolicy.State.EXPIRED;
            } else {
                try {
                    store.write(peer, received, clock.now());
                } catch (Exception ignore) {
                }
            }

            memoryCache.put(peer, selected);
            ArrayList<Pending> waiters = inFlight.remove(peer);

            if (waiters == null) {
                continue;
            }

            for (Pending pending : waiters) {
                if (selectedUsable && !(retainedExisting && pending.delivered)) {
                    dispatch(
                            peer,
                            selected,
                            selectedStale,
                            pending.callback,
                            pending.subscription,
                            pending.generation
                    );
                } else if (!pending.delivered) {
                    dispatchUnavailable(peer, pending);
                }
            }
        }
    }

    private void fail(List<FluxGramPeerKey> peers) {
        for (FluxGramPeerKey peer : peers) {
            ArrayList<Pending> waiters = inFlight.remove(peer);

            if (waiters == null) {
                continue;
            }

            for (Pending pending : waiters) {
                if (!pending.delivered) {
                    dispatchUnavailable(peer, pending);
                }
            }
        }
    }

    private void dispatch(
            FluxGramPeerKey requested,
            AppearanceApiDto.Result result,
            boolean stale,
            Callback callback,
            Subscription subscription,
            long requestGeneration
    ) {
        AppearanceProfile profile = AppearanceProfile.fromApi(result, clock.now());
        Lookup.Status status = result.getProfile() == null
                ? Lookup.Status.NONE
                : Lookup.Status.PROFILE;
        Lookup lookup = new Lookup(account, requested, result.getPeer(), profile, status, stale);
        callbackExecutor.execute(() -> {
            if (active(subscription, requestGeneration)) {
                callback.onResult(lookup);
            }
        });
    }

    private void dispatchUnavailable(FluxGramPeerKey peer, Pending pending) {
        Lookup lookup = new Lookup(
                account,
                peer,
                peer,
                null,
                Lookup.Status.UNAVAILABLE,
                false
        );
        callbackExecutor.execute(() -> {
            if (active(pending.subscription, pending.generation)) {
                pending.callback.onResult(lookup);
            }
        });
    }

    private boolean active(Subscription subscription, long requestGeneration) {
        return !subscription.isCancelled()
                && generation.get() == requestGeneration
                && ownerProvider.getOwner(account) == owner;
    }

    private boolean validResponsePeer(FluxGramPeerKey requested, FluxGramPeerKey result) {
        if (requested.equals(result)) {
            return true;
        }

        return requested.getType() == FluxGramPeerKey.Type.GROUP
                && result.getType() == FluxGramPeerKey.Type.GROUP;
    }

    private void removeInFlight(List<FluxGramPeerKey> peers, long requestGeneration) {
        for (FluxGramPeerKey peer : peers) {
            ArrayList<Pending> waiters = inFlight.get(peer);

            if (waiters != null && !waiters.isEmpty()
                    && waiters.get(0).generation == requestGeneration) {
                inFlight.remove(peer);
            }
        }
    }

    private static final class Pending {
        private final Callback callback;
        private final Subscription subscription;
        private final long generation;
        private final boolean delivered;

        private Pending(
                Callback callback,
                Subscription subscription,
                long generation,
                boolean delivered
        ) {
            this.callback = callback;
            this.subscription = subscription;
            this.generation = generation;
            this.delivered = delivered;
        }
    }
}
