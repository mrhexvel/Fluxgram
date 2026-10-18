import org.telegram.messenger.fluxgram.appearance.AppearanceApiDto;
import org.telegram.messenger.fluxgram.appearance.AppearanceCachePolicy;
import org.telegram.messenger.fluxgram.appearance.AppearanceMemoryCache;
import org.telegram.messenger.fluxgram.appearance.AppearanceRepository;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public final class AppearanceRepositoryCheck {
    private static final long NOW = 1_000_000L;
    private static final FluxGramPeerKey USER = key(FluxGramPeerKey.Type.USER, 10);
    private static final FluxGramPeerKey GROUP = key(FluxGramPeerKey.Type.GROUP, 20);
    private static final FluxGramPeerKey MIGRATED_GROUP = key(FluxGramPeerKey.Type.GROUP, 21);

    public static void main(String[] args) {
        checkPolicyAndMemoryLimit();
        checkDedupeAndCacheFirst();
        checkNegativeAndStaleBehavior();
        checkAliasAndTypeMismatch();
        checkRevisionAndCancellation();
        checkBatchLimit();
        checkOwnerRelease();
    }

    private static void checkPolicyAndMemoryLimit() {
        AppearanceCachePolicy policy = new AppearanceCachePolicy();
        expect(policy.state(positive(USER, 1, NOW), NOW) == AppearanceCachePolicy.State.FRESH,
                "fresh boundary");
        expect(policy.state(positive(USER, 1, NOW - 1), NOW) == AppearanceCachePolicy.State.STALE,
                "positive stale");
        expect(policy.state(positive(USER, 1, NOW - AppearanceCachePolicy.POSITIVE_STALE_WINDOW_MS - 1), NOW)
                        == AppearanceCachePolicy.State.EXPIRED,
                "positive stale limit");
        expect(policy.state(negative(USER, 1, NOW - 1), NOW) == AppearanceCachePolicy.State.EXPIRED,
                "negative has no stale window");

        AppearanceMemoryCache cache = new AppearanceMemoryCache();

        for (int id = 1; id <= AppearanceMemoryCache.MAX_ENTRIES + 1; id++) {
            FluxGramPeerKey peer = key(FluxGramPeerKey.Type.USER, id);
            cache.put(peer, positive(peer, 1, NOW + 1000));
        }

        expect(cache.get(key(FluxGramPeerKey.Type.USER, 1)) == null, "memory LRU limit");
    }

    private static void checkDedupeAndCacheFirst() {
        FakeStore store = new FakeStore();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork(positive(USER, 2, NOW + 1000));
        AppearanceRepository repository = repository(store, network, networkExecutor, value -> 100);
        ArrayList<AppearanceRepository.Lookup> first = new ArrayList<>();
        ArrayList<AppearanceRepository.Lookup> second = new ArrayList<>();

        repository.load(USER, first::add);
        repository.load(USER, second::add);
        expect(networkExecutor.size() == 1, "deduplicated network task");
        networkExecutor.runAll();
        expect(network.calls == 1, "deduplicated request");
        expect(first.size() == 1 && second.size() == 1, "all subscribers delivered");
        expect(first.get(0).getStatus() == AppearanceRepository.Lookup.Status.PROFILE,
                "profile status");

        repository.load(USER, first::add);
        expect(first.size() == 2, "cache-first callback");
        expect(network.calls == 1, "fresh cache skips network");
    }

    private static void checkNegativeAndStaleBehavior() {
        FakeStore negativeStore = new FakeStore();
        negativeStore.writeDirect(USER, negative(USER, 1, NOW - 1));
        FakeNetwork disabled = new FakeNetwork();
        disabled.enabled = false;
        AppearanceRepository negativeRepository = repository(
                negativeStore,
                disabled,
                Runnable::run,
                value -> 100
        );
        ArrayList<AppearanceRepository.Lookup> negative = new ArrayList<>();
        negativeRepository.load(USER, negative::add);
        expect(negative.size() == 1
                        && negative.get(0).getStatus() == AppearanceRepository.Lookup.Status.UNAVAILABLE,
                "stale negative remains empty");

        FakeStore staleStore = new FakeStore();
        staleStore.writeDirect(USER, positive(USER, 1, NOW - 1));
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork(positive(USER, 2, NOW + 1000));
        AppearanceRepository staleRepository = repository(
                staleStore,
                network,
                networkExecutor,
                value -> 100
        );
        ArrayList<AppearanceRepository.Lookup> values = new ArrayList<>();
        staleRepository.load(USER, values::add);
        expect(values.size() == 1 && values.get(0).isStale(), "stale delivered immediately");
        networkExecutor.runAll();
        expect(values.size() == 2 && !values.get(1).isStale(), "stale revalidated");
    }

    private static void checkAliasAndTypeMismatch() {
        FakeStore store = new FakeStore();
        FakeNetwork network = new FakeNetwork(positive(MIGRATED_GROUP, 3, NOW + 1000));
        AppearanceRepository repository = repository(store, network, Runnable::run, value -> 100);
        ArrayList<AppearanceRepository.Lookup> values = new ArrayList<>();
        repository.load(GROUP, values::add);
        expect(values.size() == 1, "group alias delivered");
        expect(values.get(0).getRequestedPeer().equals(GROUP), "requested alias retained");
        expect(values.get(0).getCanonicalPeer().equals(MIGRATED_GROUP), "canonical alias delivered");
        expect(store.profiles.containsKey(MIGRATED_GROUP), "canonical profile cached");
        expect(!store.profiles.containsKey(GROUP), "alias not cached as profile");

        FakeNetwork mismatch = new FakeNetwork(positive(GROUP, 4, NOW + 1000));
        AppearanceRepository mismatchRepository = repository(
                new FakeStore(),
                mismatch,
                Runnable::run,
                value -> 100
        );
        ArrayList<AppearanceRepository.Lookup> rejected = new ArrayList<>();
        mismatchRepository.load(USER, rejected::add);
        expect(rejected.size() == 1
                        && rejected.get(0).getStatus() == AppearanceRepository.Lookup.Status.UNAVAILABLE,
                "type mismatch rejected");
    }

    private static void checkRevisionAndCancellation() {
        FakeStore store = new FakeStore();
        store.writeDirect(USER, positive(USER, 10, NOW - AppearanceCachePolicy.POSITIVE_STALE_WINDOW_MS - 1));
        FakeNetwork network = new FakeNetwork(positive(USER, 9, NOW + 1000));
        AppearanceRepository repository = repository(store, network, Runnable::run, value -> 100);
        ArrayList<AppearanceRepository.Lookup> values = new ArrayList<>();
        repository.load(USER, values::add);
        expect(values.size() == 1
                        && values.get(0).getStatus() == AppearanceRepository.Lookup.Status.UNAVAILABLE,
                "older revision cannot replace expired cache");
        expect(store.profiles.get(USER).getRevision().equals(BigInteger.TEN), "newer revision retained");

        FakeStore staleStore = new FakeStore();
        staleStore.writeDirect(USER, positive(USER, 10, NOW - 1));
        AppearanceRepository staleRepository = repository(
                staleStore,
                new FakeNetwork(positive(USER, 9, NOW + 1000)),
                Runnable::run,
                value -> 100
        );
        ArrayList<AppearanceRepository.Lookup> staleValues = new ArrayList<>();
        staleRepository.load(USER, staleValues::add);
        expect(staleValues.size() == 1 && staleValues.get(0).isStale(),
                "older revision does not duplicate stale delivery");

        DeferredExecutor networkExecutor = new DeferredExecutor();
        AppearanceRepository cancellable = repository(
                new FakeStore(),
                new FakeNetwork(positive(USER, 1, NOW + 1000)),
                networkExecutor,
                value -> 100
        );
        ArrayList<AppearanceRepository.Lookup> cancelled = new ArrayList<>();
        AppearanceRepository.Subscription subscription = cancellable.load(USER, cancelled::add);
        subscription.cancel();
        networkExecutor.runAll();
        expect(cancelled.isEmpty(), "cancelled callback invalidated");

        DeferredExecutor ownerNetworkExecutor = new DeferredExecutor();
        long[] owner = {100};
        AppearanceRepository ownerAware = repository(
                new FakeStore(),
                new FakeNetwork(positive(USER, 1, NOW + 1000)),
                ownerNetworkExecutor,
                value -> owner[0]
        );
        ArrayList<AppearanceRepository.Lookup> ownerValues = new ArrayList<>();
        ownerAware.load(USER, ownerValues::add);
        owner[0] = 200;
        ownerNetworkExecutor.runAll();
        expect(ownerValues.isEmpty(), "owner change invalidates callback");

        DeferredExecutor generationExecutor = new DeferredExecutor();
        AppearanceRepository generationAware = repository(
                new FakeStore(),
                new FakeNetwork(positive(USER, 1, NOW + 1000)),
                generationExecutor,
                value -> 100
        );
        ArrayList<AppearanceRepository.Lookup> oldGeneration = new ArrayList<>();
        ArrayList<AppearanceRepository.Lookup> newGeneration = new ArrayList<>();
        generationAware.load(USER, oldGeneration::add);
        generationAware.invalidate();
        generationAware.load(USER, newGeneration::add);
        generationExecutor.runNext();
        generationExecutor.runNext();
        expect(oldGeneration.isEmpty(), "old generation ignored");
        expect(newGeneration.size() == 1, "old completion preserves new request");
    }

    private static void checkBatchLimit() {
        ArrayList<FluxGramPeerKey> peers = new ArrayList<>();

        for (int id = 1; id <= 205; id++) {
            peers.add(key(FluxGramPeerKey.Type.USER, id));
        }

        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        AppearanceRepository repository = repository(new FakeStore(), network, Runnable::run, value -> 100);
        repository.loadBatch(peers, value -> {
        });
        expect(network.batchSizes.equals(Arrays.asList(100, 100, 5)), "batch capped at 100");
    }

    private static void checkOwnerRelease() {
        long[] owner = {100};
        FakeStore oldStore = new FakeStore();
        DeferredExecutor oldWorker = new DeferredExecutor();
        DeferredExecutor oldNetwork = new DeferredExecutor();
        AppearanceRepository oldRepository = new AppearanceRepository(
                0,
                100,
                oldStore,
                new FakeNetwork(positive(USER, 1, NOW + 1000)),
                value -> owner[0],
                () -> NOW,
                oldWorker,
                oldNetwork,
                Runnable::run
        );
        ArrayList<AppearanceRepository.Lookup> oldValues = new ArrayList<>();
        oldRepository.load(USER, oldValues::add);
        oldWorker.runNext();
        expect(oldNetwork.size() == 1, "old owner request started");

        oldRepository.release();
        expect(!oldStore.closed, "release is nonblocking");
        oldWorker.runNext();
        expect(oldStore.closed, "store closed on worker");

        owner[0] = 200;
        oldNetwork.runAll();
        oldWorker.runAll();
        expect(oldValues.isEmpty(), "released owner callback ignored");
        expect(oldRepository.load(USER, oldValues::add).isCancelled(), "released repository rejects load");

        AppearanceRepository nextRepository = new AppearanceRepository(
                0,
                200,
                new FakeStore(),
                new FakeNetwork(positive(USER, 2, NOW + 1000)),
                value -> owner[0],
                () -> NOW,
                Runnable::run,
                Runnable::run,
                Runnable::run
        );
        ArrayList<AppearanceRepository.Lookup> nextValues = new ArrayList<>();
        nextRepository.load(USER, nextValues::add);
        expect(nextValues.size() == 1, "reused account owner loads independently");
    }

    private static AppearanceRepository repository(
            FakeStore store,
            FakeNetwork network,
            Executor networkExecutor,
            AppearanceRepository.OwnerProvider ownerProvider
    ) {
        return new AppearanceRepository(
                0,
                100,
                store,
                network,
                ownerProvider,
                () -> NOW,
                Runnable::run,
                networkExecutor,
                Runnable::run
        );
    }

    private static AppearanceApiDto.Result positive(
            FluxGramPeerKey peer,
            long revision,
            long expiresAt
    ) {
        AppearanceApiDto.Badge badge = new AppearanceApiDto.Badge(
                "developer",
                5373141891321699086L,
                "Developer",
                "Description",
                true,
                null
        );
        AppearanceApiDto.Profile profile = new AppearanceApiDto.Profile(
                Collections.singletonList(badge),
                null
        );
        return new AppearanceApiDto.Result(
                peer,
                BigInteger.valueOf(revision),
                expiresAt,
                profile,
                "{}"
        );
    }

    private static AppearanceApiDto.Result negative(
            FluxGramPeerKey peer,
            long revision,
            long expiresAt
    ) {
        return new AppearanceApiDto.Result(
                peer,
                BigInteger.valueOf(revision),
                expiresAt,
                null,
                "{}"
        );
    }

    private static FluxGramPeerKey key(FluxGramPeerKey.Type type, long id) {
        return new FluxGramPeerKey(type, id);
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }

    private static final class FakeStore implements AppearanceRepository.Store {
        private final Map<FluxGramPeerKey, AppearanceApiDto.Result> profiles = new HashMap<>();
        private final Map<FluxGramPeerKey, FluxGramPeerKey> aliases = new HashMap<>();
        private boolean closed;

        @Override
        public AppearanceApiDto.Result read(FluxGramPeerKey requested) {
            FluxGramPeerKey canonical = aliases.get(requested);
            return profiles.get(canonical == null ? requested : canonical);
        }

        @Override
        public AppearanceApiDto.Result readCanonical(FluxGramPeerKey canonical) {
            return profiles.get(canonical);
        }

        @Override
        public void write(FluxGramPeerKey requested, AppearanceApiDto.Result result, long storedAt) {
            profiles.put(result.getPeer(), result);

            if (!requested.equals(result.getPeer())) {
                aliases.put(requested, result.getPeer());
            }
        }

        @Override
        public void clear() {
            profiles.clear();
            aliases.clear();
        }

        @Override
        public void close() {
            closed = true;
        }

        private void writeDirect(FluxGramPeerKey requested, AppearanceApiDto.Result result) {
            write(requested, result, NOW);
        }
    }

    private static final class FakeNetwork implements AppearanceRepository.Network {
        private final ArrayList<AppearanceApiDto.Result> results = new ArrayList<>();
        private final ArrayList<Integer> batchSizes = new ArrayList<>();
        private boolean enabled = true;
        private boolean echo;
        private int calls;

        private FakeNetwork(AppearanceApiDto.Result... values) {
            results.addAll(Arrays.asList(values));
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public List<AppearanceApiDto.Result> fetch(List<FluxGramPeerKey> peers) {
            calls++;
            batchSizes.add(peers.size());

            if (echo) {
                ArrayList<AppearanceApiDto.Result> values = new ArrayList<>();

                for (FluxGramPeerKey peer : peers) {
                    values.add(negative(peer, 1, NOW + 1000));
                }

                return values;
            }

            return new ArrayList<>(results);
        }
    }

    private static final class DeferredExecutor implements Executor {
        private final ArrayList<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable runnable) {
            tasks.add(runnable);
        }

        private int size() {
            return tasks.size();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }

        private void runNext() {
            tasks.remove(0).run();
        }
    }
}
