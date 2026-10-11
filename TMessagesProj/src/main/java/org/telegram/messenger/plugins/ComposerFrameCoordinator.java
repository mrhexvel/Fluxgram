package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.function.LongSupplier;

final class ComposerFrameCoordinator {
    private static final long MIN_FRAME_INTERVAL_NANOS = 16_000_000L;

    interface Driver {
        void post(long delayMillis);

        void cancel();
    }

    interface Client {
        boolean isActive();

        boolean onFrame(long frameTimeNanos);

        default void onInactive() {
        }
    }

    private final Driver driver;
    private final LongSupplier clock;
    private final LinkedHashSet<Client> pending = new LinkedHashSet<>();
    private boolean posted;
    private boolean dispatching;
    private long nextFrameTime;
    long frames;
    long paced;
    long deferred;
    long requests;

    ComposerFrameCoordinator(Driver driver, LongSupplier clock) {
        this.driver = driver;
        this.clock = clock;
    }

    void request(Client client) {
        if (!client.isActive()) {
            return;
        }

        requests++;

        if (pending.size() < 32 || pending.contains(client)) {
            pending.add(client);
        }

        schedule();
    }

    void cancel(Client client) {
        pending.remove(client);

        if (pending.isEmpty() && posted) {
            driver.cancel();
            posted = false;
        }
    }

    int pendingCount() {
        return pending.size();
    }

    void frame(long frameTimeNanos) {
        posted = false;

        if (frameTimeNanos < nextFrameTime) {
            paced++;
            schedule();
            return;
        }

        nextFrameTime = frameTimeNanos + MIN_FRAME_INTERVAL_NANOS;
        dispatching = true;
        frames++;
        long start = clock.getAsLong();
        ArrayList<Client> again = new ArrayList<>();

        try {
            for (Client client : new ArrayList<>(pending)) {
                if (clock.getAsLong() - start >= 2_000_000L) {
                    deferred += pending.size();
                    break;
                }

                if (!pending.remove(client)) {
                    continue;
                }

                if (!client.isActive()) {
                    client.onInactive();
                    continue;
                }

                if (client.onFrame(frameTimeNanos) && client.isActive()) {
                    again.add(client);
                }
            }

            for (Client client : again) {
                if (client.isActive()) {
                    pending.add(client);
                }
            }
        } finally {
            dispatching = false;
            schedule();
        }
    }

    private void schedule() {
        if (!posted && !dispatching && !pending.isEmpty()) {
            posted = true;
            long delay = Math.max(0, nextFrameTime - clock.getAsLong()) / 1_000_000L;
            driver.post(delay);
        }
    }
}
