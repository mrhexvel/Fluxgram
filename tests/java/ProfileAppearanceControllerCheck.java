import org.telegram.messenger.fluxgram.appearance.AppearanceApiDto;
import org.telegram.messenger.fluxgram.appearance.AppearanceRepository;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.fluxgram.appearance.FluxGramProfileAppearanceController;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public final class ProfileAppearanceControllerCheck {
    private static final long NOW = 1_000_000L;
    private static final FluxGramPeerKey USER = key(FluxGramPeerKey.Type.USER, 10);
    private static final FluxGramPeerKey OTHER_USER = key(FluxGramPeerKey.Type.USER, 11);
    private static final FluxGramPeerKey GROUP = key(FluxGramPeerKey.Type.GROUP, 20);
    private static final FluxGramPeerKey MIGRATED_GROUP = key(FluxGramPeerKey.Type.GROUP, 21);
    private static final FluxGramPeerKey CHANNEL = key(FluxGramPeerKey.Type.CHANNEL, 30);

    public static void main(String[] args) {
        checkStaleFreshAndRepeat();
        checkPeerSwitchAndDestroy();
        checkGroupAliasAndScope();
        checkAccountMismatch();
    }

    private static void checkStaleFreshAndRepeat() {
        FakeStore store = new FakeStore();
        store.writeDirect(USER, positive(USER, 1, NOW - 1, true));
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork(positive(USER, 2, NOW + 1000, false));
        Target target = new Target();
        FluxGramProfileAppearanceController controller = controller(store, network, networkExecutor, target);

        controller.bind(USER);
        expect(target.values.size() == 2, "switch clears before stale delivery");
        expect(!target.values.get(1).animated, "first stale delivery is not animated");
        networkExecutor.runAll();
        expect(target.values.size() == 3, "fresh result follows stale result");
        expect(target.values.get(2).animated, "stale to fresh delivery is animated");
        expect(!target.values.get(2).badge.hasParticles(), "fresh particles value delivered");

        controller.bind(USER);
        expect(network.calls == 1, "same peer does not trigger duplicate lookup");
        expect(target.values.size() == 4 && target.values.get(3).badge == target.values.get(2).badge,
                "same peer reapplies cached badge");
    }

    private static void checkPeerSwitchAndDestroy() {
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork(
                positive(USER, 1, NOW + 1000, true),
                positive(OTHER_USER, 1, NOW + 1000, true)
        );
        Target target = new Target();
        FluxGramProfileAppearanceController controller = controller(
                new FakeStore(),
                network,
                networkExecutor,
                target
        );

        controller.bind(USER);
        controller.bind(OTHER_USER);
        expect(target.values.size() == 2
                        && target.values.get(0).badge == null
                        && target.values.get(1).badge == null,
                "peer switch clears immediately");
        networkExecutor.runNext();
        expect(target.values.size() == 2, "old peer callback ignored");
        networkExecutor.runNext();
        expect(target.values.size() == 3, "current peer callback delivered");

        controller.bind(USER);
        controller.destroy();
        expect(target.values.get(target.values.size() - 1).badge == null, "destroy clears target");
        int deliveries = target.values.size();
        networkExecutor.runAll();
        expect(target.values.size() == deliveries, "destroy ignores pending callback");
    }

    private static void checkGroupAliasAndScope() {
        FakeNetwork network = new FakeNetwork(positive(MIGRATED_GROUP, 1, NOW + 1000, true));
        Target target = new Target();
        FluxGramProfileAppearanceController controller = controller(
                new FakeStore(),
                network,
                Runnable::run,
                target
        );

        controller.bind(GROUP);
        expect(target.last().badge != null, "group alias uses requested peer for validation");
        int calls = network.calls;
        controller.bind(CHANNEL);
        expect(network.calls == calls && target.last().badge == null, "channel is not loaded");
        controller.bind(null);
        expect(network.calls == calls, "unsupported peer repeat remains local");
    }

    private static void checkAccountMismatch() {
        FakeNetwork network = new FakeNetwork(positive(USER, 1, NOW + 1000, true));
        AppearanceRepository repository = new AppearanceRepository(
                0,
                100,
                new FakeStore(),
                network,
                value -> 100,
                () -> NOW,
                Runnable::run,
                Runnable::run,
                Runnable::run
        );
        Target target = new Target();
        FluxGramProfileAppearanceController controller = new FluxGramProfileAppearanceController(
                1,
                repository,
                target
        );

        controller.bind(USER);
        expect(target.values.size() == 1 && target.last().badge == null,
                "account mismatch is ignored");
    }

    private static FluxGramProfileAppearanceController controller(
            FakeStore store,
            FakeNetwork network,
            Executor networkExecutor,
            Target target
    ) {
        AppearanceRepository repository = new AppearanceRepository(
                0,
                100,
                store,
                network,
                value -> 100,
                () -> NOW,
                Runnable::run,
                networkExecutor,
                Runnable::run
        );
        return new FluxGramProfileAppearanceController(0, repository, target);
    }

    private static AppearanceApiDto.Result positive(
            FluxGramPeerKey peer,
            long revision,
            long expiresAt,
            boolean particles
    ) {
        AppearanceApiDto.Badge badge = new AppearanceApiDto.Badge(
                "developer",
                5373141891321699086L,
                "Developer",
                "",
                particles,
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

    private static FluxGramPeerKey key(FluxGramPeerKey.Type type, long id) {
        return new FluxGramPeerKey(type, id);
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }

    private static final class Target implements FluxGramProfileAppearanceController.Target {
        private final ArrayList<Value> values = new ArrayList<>();

        @Override
        public void apply(ProfileBadge badge, boolean animated) {
            values.add(new Value(badge, animated));
        }

        private Value last() {
            return values.get(values.size() - 1);
        }
    }

    private static final class Value {
        private final ProfileBadge badge;
        private final boolean animated;

        private Value(ProfileBadge badge, boolean animated) {
            this.badge = badge;
            this.animated = animated;
        }
    }

    private static final class FakeStore implements AppearanceRepository.Store {
        private final Map<FluxGramPeerKey, AppearanceApiDto.Result> profiles = new HashMap<>();
        private final Map<FluxGramPeerKey, FluxGramPeerKey> aliases = new HashMap<>();

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

        private void writeDirect(FluxGramPeerKey requested, AppearanceApiDto.Result result) {
            write(requested, result, NOW);
        }
    }

    private static final class FakeNetwork implements AppearanceRepository.Network {
        private final ArrayList<AppearanceApiDto.Result> results = new ArrayList<>();
        private int calls;

        private FakeNetwork(AppearanceApiDto.Result... values) {
            Collections.addAll(results, values);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<AppearanceApiDto.Result> fetch(List<FluxGramPeerKey> peers) {
            AppearanceApiDto.Result result = results.get(Math.min(calls, results.size() - 1));
            calls++;
            return Collections.singletonList(result);
        }
    }

    private static final class DeferredExecutor implements Executor {
        private final ArrayList<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable runnable) {
            tasks.add(runnable);
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
