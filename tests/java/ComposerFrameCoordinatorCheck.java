package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ComposerFrameCoordinatorCheck {
    private static long clock;
    private static int posts;
    private static int cancellations;
    private static long postedDelay;

    private static final class Client implements ComposerFrameCoordinator.Client {
        final int id;
        final List<Integer> order;
        boolean active = true;
        boolean again;
        long cost;
        long timestamp;
        int inactive;

        Client(int id, List<Integer> order) {
            this.id = id;
            this.order = order;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public boolean onFrame(long frameTimeNanos) {
            timestamp = frameTimeNanos;
            order.add(id);
            clock += cost;
            return again;
        }

        @Override
        public void onInactive() {
            inactive++;
        }
    }

    public static void main(String[] args) {
        ComposerFrameCoordinator coordinator = new ComposerFrameCoordinator(new ComposerFrameCoordinator.Driver() {
            @Override
            public void post(long delayMillis) {
                posts++;
                postedDelay = delayMillis;
            }

            @Override
            public void cancel() {
                cancellations++;
            }
        }, () -> clock);
        List<Integer> order = new ArrayList<>();
        Client first = new Client(1, order);
        Client second = new Client(2, order);
        coordinator.request(first);
        coordinator.request(first);
        coordinator.request(second);
        require(posts == 1 && coordinator.pendingCount() == 2, "requests were not coalesced");
        frame(coordinator, 1_000_000_000L);
        require(order.equals(Arrays.asList(1, 2)) && first.timestamp == 1_000_000_000L, "frame order/timestamp");
        require(coordinator.pendingCount() == 0 && posts == 1, "idle animation kept scheduling");

        order.clear();
        first.again = true;
        first.cost = 3_000_000;
        coordinator.request(first);
        coordinator.request(second);
        frame(coordinator, 2_000_000_000L);
        require(order.equals(Arrays.asList(1)), "aggregate frame budget ignored");
        require(coordinator.pendingCount() == 2 && coordinator.deferred == 1, "deferred work lost");
        first.cost = 0;
        first.again = false;
        frame(coordinator, 2_020_000_000L);
        require(order.equals(Arrays.asList(1, 2, 1)), "busy effect starved another effect");
        require(coordinator.pendingCount() == 0, "final frame left demand");

        coordinator.request(first);
        first.active = false;
        int before = order.size();
        frame(coordinator, 3_000_000_000L);
        require(order.size() == before && coordinator.pendingCount() == 0, "stale generation delivered");
        require(first.inactive == 1, "stale frame did not notify cleanup");
        first.active = true;
        coordinator.request(first);
        coordinator.cancel(first);
        require(cancellations == 1 && coordinator.pendingCount() == 0, "detach did not cancel pending vsync");

        for (int i = 0; i < 40; i++) {
            coordinator.request(new Client(i, order));
        }

        require(coordinator.pendingCount() == 32, "demand registry is unbounded");
        frame(coordinator, 4_000_000_000L);
        require(coordinator.pendingCount() == 0, "bounded frame did not drain");

        first.again = true;
        clock = 10_000_000_000L;
        coordinator.request(first);
        require(postedDelay == 0, "new demand after idle was delayed");
        frame(coordinator, 10_000_000_000L);
        int pacedBefore = order.size();
        require(postedDelay == 16, "next vsync was not paced");
        frame(coordinator, 10_008_333_333L);
        require(order.size() == pacedBefore && coordinator.paced == 1, "120Hz frame escaped pacing");
        frame(coordinator, 10_016_666_666L);
        require(order.size() == pacedBefore + 1, "60Hz demand was skipped");
        coordinator.cancel(first);
        require(coordinator.pendingCount() == 0, "paced callback survived cancellation");
    }

    private static void frame(ComposerFrameCoordinator coordinator, long timestamp) {
        clock = timestamp;
        coordinator.frame(timestamp);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
