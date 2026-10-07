import org.telegram.messenger.fluxgram.appearance.AppearanceApiDto;
import org.telegram.messenger.fluxgram.appearance.AppearanceCachePolicy;
import org.telegram.messenger.fluxgram.appearance.AppearanceRepository;
import org.telegram.messenger.fluxgram.appearance.FluxGramPeerKey;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.fluxgram.appearance.FluxGramDialogListAppearanceController;
import org.telegram.ui.fluxgram.appearance.FluxGramUserListAppearanceController;
import org.telegram.ui.fluxgram.appearance.UserListBadgePlacement;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public final class UserListAppearanceControllerCheck {
    private static final long NOW = 1_000_000L;
    private static final FluxGramPeerKey USER = key(1);
    private static final FluxGramPeerKey OTHER_USER = key(2);

    public static void main(String[] args) {
        checkFrameCoalescingAndChunks();
        checkKnownNoneAndPending();
        checkRecycleSafety();
        checkStaleFreshAnimation();
        checkDestroyCancelsAll();
        checkAccountMismatch();
        checkDialogFrameCoalescingAndChunks();
        checkDialogDropsUnboundBeforeFlush();
        checkDialogSupportsGroupsOnly();
        checkDialogKnownNoneAndRecycleSafety();
        checkDialogStaleFreshAndDestroy();
        checkDialogEvictedCallbackCannotReachReplacement();
        checkDialogStaleFailuresRemainBounded();
        checkPlacement();
    }

    private static void checkFrameCoalescingAndChunks() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramUserListAppearanceController controller = controller(
                0,
                new FakeStore(),
                network,
                Runnable::run,
                frames
        );

        for (int id = 1; id <= 205; id++) {
            Target target = new Target();
            target.requestedPeer = key(id);
            controller.bind(target, target.requestedPeer);
        }

        expect(frames.size() == 1, "binds coalesce into one UI frame");
        expect(network.batchSizes.isEmpty(), "network is not started before frame");
        frames.runAll();
        expect(network.batchSizes.equals(Arrays.asList(100, 100, 5)), "controller chunks batches at 100");
    }

    private static void checkKnownNoneAndPending() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        FakeNetwork network = new FakeNetwork(negative(USER, 1, NOW + 1000));
        FluxGramUserListAppearanceController controller = controller(
                0,
                new FakeStore(),
                network,
                Runnable::run,
                frames
        );
        Target first = new Target(USER);
        Target second = new Target(USER);

        controller.bind(first, USER);
        controller.bind(second, USER);
        expect(first.values.size() == 1 && second.values.size() == 1, "pending targets clear immediately");
        expect(frames.size() == 1, "duplicate pending key remains one frame request");
        frames.runAll();
        expect(network.calls == 1, "duplicate pending key is loaded once");

        Target rebound = new Target(USER);
        controller.bind(rebound, USER);
        expect(frames.size() == 0, "known NONE does not become pending again");
        expect(network.calls == 1 && rebound.last().badge == null, "known NONE is reused");
    }

    private static void checkRecycleSafety() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramUserListAppearanceController controller = controller(
                0,
                new FakeStore(),
                network,
                networkExecutor,
                frames
        );
        Target target = new Target(USER);

        controller.bind(target, USER);
        frames.runAll();
        target.requestedPeer = OTHER_USER;
        controller.bind(target, OTHER_USER);
        frames.runAll();
        networkExecutor.runNext();
        expect(target.nonNullCount() == 0, "recycled target ignores old keyed result");
        networkExecutor.runNext();
        expect(target.nonNullCount() == 1, "recycled target accepts current keyed result");
    }

    private static void checkStaleFreshAnimation() {
        FakeStore store = new FakeStore();
        store.writeDirect(USER, positive(USER, 1, NOW - 1, true));
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork(positive(USER, 2, NOW + 1000, false));
        FluxGramUserListAppearanceController controller = controller(
                0,
                store,
                network,
                networkExecutor,
                frames
        );
        Target target = new Target(USER);

        controller.bind(target, USER);
        frames.runAll();
        expect(target.nonNullCount() == 1 && !target.last().animated, "first stale result is not animated");
        networkExecutor.runAll();
        expect(target.nonNullCount() == 2 && target.last().animated, "fresh result animates after stale");
        expect(!target.last().badge.hasParticles(), "fresh badge replaces stale badge");
    }

    private static void checkDestroyCancelsAll() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramUserListAppearanceController controller = controller(
                0,
                new FakeStore(),
                network,
                networkExecutor,
                frames
        );
        Target beforeFrame = new Target(USER);
        controller.bind(beforeFrame, USER);
        controller.destroy();
        expect(frames.size() == 0, "destroy cancels queued UI frame");
        expect(beforeFrame.destroyed, "destroy releases registered target");

        DeferredFrameScheduler pendingFrames = new DeferredFrameScheduler();
        FluxGramUserListAppearanceController pendingController = controller(
                0,
                new FakeStore(),
                network,
                networkExecutor,
                pendingFrames
        );
        Target pending = new Target(USER);
        pendingController.bind(pending, USER);
        pendingFrames.runAll();
        int deliveries = pending.values.size();
        pendingController.destroy();
        networkExecutor.runAll();
        expect(pending.values.size() == deliveries, "destroy cancels repository subscriptions");
        expect(pending.destroyed, "destroy releases pending target");
    }

    private static void checkAccountMismatch() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        FakeNetwork network = new FakeNetwork(positive(USER, 1, NOW + 1000, true));
        FluxGramUserListAppearanceController controller = controller(
                1,
                new FakeStore(),
                network,
                Runnable::run,
                frames
        );
        Target target = new Target(USER);

        controller.bind(target, USER);
        frames.runAll();
        expect(target.nonNullCount() == 0, "account mismatch is rejected");
    }

    private static void checkDialogFrameCoalescingAndChunks() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramDialogListAppearanceController controller = dialogController(
                0,
                new FakeStore(),
                network,
                Runnable::run,
                frames
        );

        for (int id = 1; id <= 205; id++) {
            Target target = new Target();
            target.requestedPeer = id % 2 == 0 ? groupKey(id) : key(id);
            controller.bind(target, target.requestedPeer);
        }

        expect(frames.size() == 1, "dialog binds coalesce into one UI frame");
        frames.runAll();
        expect(network.batchSizes.equals(Arrays.asList(100, 100, 5)), "dialog controller chunks batches at 100");
    }

    private static void checkDialogDropsUnboundBeforeFlush() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramDialogListAppearanceController controller = dialogController(
                0,
                new FakeStore(),
                network,
                Runnable::run,
                frames
        );
        Target removed = new Target(USER);

        controller.bind(removed, USER);
        controller.unbind(removed);

        expect(frames.size() == 0, "last detached dialog cancels the pending frame");
        expect(network.calls == 0, "detached dialog is not looked up");

        Target first = new Target(USER);
        Target second = new Target(USER);
        controller.bind(first, USER);
        controller.bind(second, USER);
        controller.unbind(first);
        frames.runAll();

        expect(network.calls == 1, "shared dialog peer remains queued for its attached target");
        expect(second.nonNullCount() == 1, "remaining dialog target receives the result");
    }

    private static void checkDialogSupportsGroupsOnly() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramDialogListAppearanceController controller = dialogController(
                0,
                new FakeStore(),
                network,
                Runnable::run,
                frames
        );
        Target group = new Target(groupKey(10));
        Target channel = new Target(new FluxGramPeerKey(FluxGramPeerKey.Type.CHANNEL, 11));

        controller.bind(group, group.requestedPeer);
        controller.bind(channel, channel.requestedPeer);
        frames.runAll();

        expect(group.nonNullCount() == 1, "group dialog is supported");
        expect(channel.nonNullCount() == 0, "channel dialog is rejected");
        expect(network.batchSizes.equals(Collections.singletonList(1)), "unsupported dialog is absent from the batch");
    }

    private static void checkDialogKnownNoneAndRecycleSafety() {
        DeferredFrameScheduler noneFrames = new DeferredFrameScheduler();
        FakeNetwork noneNetwork = new FakeNetwork(negative(USER, 1, NOW + 1000));
        FluxGramDialogListAppearanceController noneController = dialogController(
                0,
                new FakeStore(),
                noneNetwork,
                Runnable::run,
                noneFrames
        );
        Target first = new Target(USER);

        noneController.bind(first, USER);
        noneFrames.runAll();
        Target rebound = new Target(USER);
        noneController.bind(rebound, USER);

        expect(noneFrames.size() == 0, "known dialog NONE is reused");
        expect(noneNetwork.calls == 1, "known dialog NONE is not requested again");

        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramDialogListAppearanceController controller = dialogController(
                0,
                new FakeStore(),
                network,
                networkExecutor,
                frames
        );
        Target target = new Target(USER);

        controller.bind(target, USER);
        frames.runAll();
        target.requestedPeer = OTHER_USER;
        controller.bind(target, OTHER_USER);
        frames.runAll();
        networkExecutor.runNext();
        expect(target.nonNullCount() == 0, "dialog target ignores its old keyed result");
        networkExecutor.runNext();
        expect(target.nonNullCount() == 1, "dialog target accepts its current keyed result");
    }

    private static void checkDialogStaleFreshAndDestroy() {
        FakeStore store = new FakeStore();
        store.writeDirect(USER, positive(USER, 1, NOW - 1, true));
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork(positive(USER, 2, NOW + 1000, false));
        FluxGramDialogListAppearanceController controller = dialogController(
                0,
                store,
                network,
                networkExecutor,
                frames
        );
        Target target = new Target(USER);

        controller.bind(target, USER);
        frames.runAll();
        expect(target.nonNullCount() == 1 && !target.last().animated, "first stale dialog result is not animated");
        networkExecutor.runAll();
        expect(target.nonNullCount() == 2 && target.last().animated, "fresh dialog result animates after stale");

        DeferredFrameScheduler pendingFrames = new DeferredFrameScheduler();
        DeferredExecutor pendingExecutor = new DeferredExecutor();
        FakeNetwork pendingNetwork = new FakeNetwork();
        pendingNetwork.echo = true;
        FluxGramDialogListAppearanceController pendingController = dialogController(
                0,
                new FakeStore(),
                pendingNetwork,
                pendingExecutor,
                pendingFrames
        );
        Target pending = new Target(USER);
        pendingController.bind(pending, USER);
        pendingFrames.runAll();
        int deliveries = pending.values.size();
        pendingController.destroy();
        pendingExecutor.runAll();

        expect(pending.values.size() == deliveries, "destroy cancels dialog repository subscriptions");
        expect(pending.destroyed, "destroy releases dialog target");

        DeferredFrameScheduler accountFrames = new DeferredFrameScheduler();
        FakeNetwork accountNetwork = new FakeNetwork(positive(USER, 1, NOW + 1000, true));
        FluxGramDialogListAppearanceController accountController = dialogController(
                1,
                new FakeStore(),
                accountNetwork,
                Runnable::run,
                accountFrames
        );
        Target wrongAccount = new Target(USER);
        accountController.bind(wrongAccount, USER);
        accountFrames.runAll();

        expect(wrongAccount.nonNullCount() == 0, "dialog account mismatch is rejected");
    }

    private static void checkDialogEvictedCallbackCannotReachReplacement() {
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork();
        network.echo = true;
        FluxGramDialogListAppearanceController controller = dialogController(
                0,
                new FakeStore(),
                network,
                networkExecutor,
                frames
        );
        Target target = new Target(USER);

        controller.bind(target, USER);
        frames.runAll();
        controller.unbind(target);

        for (int id = 1000; id < 1512; id++) {
            Target visible = new Target(key(id));
            controller.bind(visible, visible.requestedPeer);
        }

        controller.bind(target, USER);
        frames.runAll();
        networkExecutor.runNext();

        expect(target.nonNullCount() == 1, "evicted request callback cannot update a replacement dialog entry");
        expect(!target.last().animated, "replacement dialog entry receives its first result without animation");
    }

    private static void checkDialogStaleFailuresRemainBounded() {
        FakeStore store = new FakeStore();
        DeferredFrameScheduler frames = new DeferredFrameScheduler();
        DeferredExecutor networkExecutor = new DeferredExecutor();
        FakeNetwork network = new FakeNetwork();
        FluxGramDialogListAppearanceController controller = dialogController(
                0,
                store,
                network,
                networkExecutor,
                frames
        );

        for (int id = 2000; id < 2033; id++) {
            FluxGramPeerKey peer = key(id);
            store.writeDirect(peer, positive(peer, 1, NOW - 1, true));
            controller.bind(new Target(peer), peer);
            frames.runAll();
            networkExecutor.runNext();
        }

        expect(activeDialogRequests(controller) == 32, "failed stale dialog revalidations remain bounded");
    }

    private static int activeDialogRequests(FluxGramDialogListAppearanceController controller) {
        try {
            Field field = FluxGramDialogListAppearanceController.class.getDeclaredField("requests");
            field.setAccessible(true);
            return ((java.util.Set<?>) field.get(controller)).size();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("cannot inspect dialog requests", exception);
        }
    }

    private static void checkPlacement() {
        expect(UserListBadgePlacement.calculate(false, 20, 80, 10, 120, 4, 20) == 84,
                "LTR badge follows official cluster");
        expect(UserListBadgePlacement.calculate(true, 40, 100, 10, 120, 4, 20) == 16,
                "RTL badge follows official cluster");
        expect(UserListBadgePlacement.calculate(false, 20, 100, 10, 120, 4, 20)
                        == UserListBadgePlacement.HIDDEN,
                "badge hides instead of reserving text width");
        expect(UserListBadgePlacement.calculate(true, 30, 100, 10, 120, 4, 20)
                        == UserListBadgePlacement.HIDDEN,
                "RTL badge hides when room is insufficient");
        expect(UserListBadgePlacement.calculate(false, 20, 70, 10, 120, 4, 20) == 74,
                "LTR badge uses name bounds already reserved around options");
        expect(UserListBadgePlacement.calculate(true, 50, 100, 10, 120, 4, 20) == 26,
                "RTL badge uses name bounds already reserved around options");
        expect(UserListBadgePlacement.calculate(false, 20, 70, 10, 80, 4, 20)
                        == UserListBadgePlacement.HIDDEN,
                "LTR badge hides at the reserved name edge");
        expect(UserListBadgePlacement.calculate(true, 50, 100, 40, 120, 4, 20)
                        == UserListBadgePlacement.HIDDEN,
                "RTL badge hides at the reserved name edge");
    }

    private static FluxGramUserListAppearanceController controller(
            int controllerAccount,
            FakeStore store,
            FakeNetwork network,
            Executor networkExecutor,
            DeferredFrameScheduler frames
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
        return new FluxGramUserListAppearanceController(
                controllerAccount,
                repository,
                frames
        );
    }

    private static FluxGramDialogListAppearanceController dialogController(
            int controllerAccount,
            FakeStore store,
            FakeNetwork network,
            Executor networkExecutor,
            DeferredFrameScheduler frames
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
        return new FluxGramDialogListAppearanceController(
                controllerAccount,
                repository,
                frames
        );
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

    private static FluxGramPeerKey key(long id) {
        return new FluxGramPeerKey(FluxGramPeerKey.Type.USER, id);
    }

    private static FluxGramPeerKey groupKey(long id) {
        return new FluxGramPeerKey(FluxGramPeerKey.Type.GROUP, id);
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }

    private static final class Target implements FluxGramUserListAppearanceController.Target, FluxGramDialogListAppearanceController.Target {
        private final ArrayList<Value> values = new ArrayList<>();
        private FluxGramPeerKey requestedPeer;
        private boolean destroyed;

        private Target() {
        }

        private Target(FluxGramPeerKey requestedPeer) {
            this.requestedPeer = requestedPeer;
        }

        @Override
        public FluxGramPeerKey getRequestedPeer() {
            return requestedPeer;
        }

        @Override
        public void apply(ProfileBadge badge, boolean animated) {
            values.add(new Value(badge, animated));
        }

        @Override
        public void destroyAppearance() {
            destroyed = true;
        }

        private Value last() {
            return values.get(values.size() - 1);
        }

        private int nonNullCount() {
            int count = 0;
            for (Value value : values) {
                if (value.badge != null) {
                    count++;
                }
            }
            return count;
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

    private static final class DeferredFrameScheduler implements FluxGramUserListAppearanceController.FrameScheduler, FluxGramDialogListAppearanceController.FrameScheduler {
        private final ArrayList<Runnable> tasks = new ArrayList<>();

        @Override
        public void post(Runnable runnable) {
            tasks.add(runnable);
        }

        @Override
        public void cancel(Runnable runnable) {
            tasks.remove(runnable);
        }

        private int size() {
            return tasks.size();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove(0).run();
            }
        }
    }

    private static final class DeferredExecutor implements Executor {
        private final ArrayList<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable runnable) {
            tasks.add(runnable);
        }

        private void runNext() {
            tasks.remove(0).run();
        }

        private void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }
    }

    private static final class FakeStore implements AppearanceRepository.Store {
        private final Map<FluxGramPeerKey, AppearanceApiDto.Result> values = new HashMap<>();

        @Override
        public AppearanceApiDto.Result read(FluxGramPeerKey requested) {
            return values.get(requested);
        }

        @Override
        public AppearanceApiDto.Result readCanonical(FluxGramPeerKey canonical) {
            return values.get(canonical);
        }

        @Override
        public void write(FluxGramPeerKey requested, AppearanceApiDto.Result result, long storedAt) {
            values.put(result.getPeer(), result);
        }

        @Override
        public void clear() {
            values.clear();
        }

        private void writeDirect(FluxGramPeerKey requested, AppearanceApiDto.Result result) {
            write(requested, result, NOW);
        }
    }

    private static final class FakeNetwork implements AppearanceRepository.Network {
        private final ArrayList<AppearanceApiDto.Result> results = new ArrayList<>();
        private final ArrayList<Integer> batchSizes = new ArrayList<>();
        private boolean echo;
        private int calls;

        private FakeNetwork(AppearanceApiDto.Result... results) {
            Collections.addAll(this.results, results);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public List<AppearanceApiDto.Result> fetch(List<FluxGramPeerKey> peers) {
            batchSizes.add(peers.size());
            calls++;

            if (echo) {
                ArrayList<AppearanceApiDto.Result> values = new ArrayList<>();
                for (FluxGramPeerKey peer : peers) {
                    values.add(positive(peer, 1, NOW + 1000, true));
                }
                return values;
            }

            return Collections.singletonList(results.get(Math.min(calls - 1, results.size() - 1)));
        }
    }
}
