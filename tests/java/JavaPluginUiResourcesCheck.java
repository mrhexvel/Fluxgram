package org.telegram.messenger.plugins;

import org.telegram.messenger.plugins.api.JavaPluginUiResource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class JavaPluginUiResourcesCheck {
    private static final class Queue implements JavaPluginUiResources.Dispatcher {
        final ArrayDeque<Runnable> actions = new ArrayDeque<>();
        boolean ui;

        @Override
        public synchronized void post(Runnable action) {
            actions.add(action);
        }

        @Override
        public boolean isUiThread() {
            return ui;
        }

        void drain() {
            ui = true;

            try {
                int limit = 100;

                while (!actions.isEmpty()) {
                    require(limit-- > 0, "UI dispatch busy loop");
                    actions.remove().run();
                }
            } finally {
                ui = false;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Queue queue = new Queue();
        AtomicBoolean active = new AtomicBoolean(true);
        List<Throwable> errors = new ArrayList<>();
        List<String> calls = new ArrayList<>();
        JavaPluginUiResources registry = createRegistry(queue, active::get, errors::add);
        JavaPluginUiResource first = registry.register(() -> {
            require(queue.ui, "factory thread");
            calls.add("create1");
            return () -> {
                require(queue.ui, "cleanup thread");
                calls.add("close1");
            };
        });
        registry.register(() -> {
            calls.add("create2");
            return () -> calls.add("close2");
        });
        require(queue.actions.isEmpty(), "factory published before RUNNING");
        registry.activate();
        require(queue.actions.size() == 1, "registration not coalesced");
        queue.drain();
        require(first.isActive() && !first.isClosed(), "active handle");
        active.set(false);
        require(!first.isActive() && !first.isClosed(), "invalidation versus acknowledgement");
        registry.closeAsync(error -> {
            require(error == null && queue.ui, "completion contract");
            calls.add("done");
        });
        require(!first.isClosed(), "cleanup acknowledged before main thread");
        queue.drain();
        require(first.isClosed(), "cleanup not acknowledged");
        require(calls.equals(Arrays.asList("create1", "create2", "close2", "close1", "done")), "LIFO: " + calls);
        first.close();
        require(queue.actions.isEmpty(), "closed handle posted again");

        active.set(true);
        calls.clear();
        JavaPluginUiResources failed = createRegistry(queue, active::get, errors::add);
        failed.register(() -> () -> calls.add("ok"));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        JavaPluginUiResource retry = failed.register(() -> () -> {
            calls.add("retry");

            if (failOnce.getAndSet(false)) {
                throw new AssertionError("close failure");
            }
        });
        failed.activate();
        queue.drain();
        failed.closeAsync(error -> require(error != null, "cleanup error was lost"));
        queue.drain();
        require(!retry.isActive() && !retry.isClosed(), "failed handle falsely closed");
        require(calls.equals(Arrays.asList("retry", "ok")), "cleanup stopped at failure");
        failed.closeAsync(error -> require(error == null, "retry failed"));
        queue.drain();
        require(calls.equals(Arrays.asList("retry", "ok", "retry")), "successful step repeated");
        require(retry.isClosed(), "retry did not close");

        JavaPluginUiResources cancelled = createRegistry(queue, active::get, errors::add);
        JavaPluginUiResource cancelHandle = cancelled.register(() -> {
            throw new AssertionError("cancelled factory ran");
        });
        AtomicBoolean siblingCreated = new AtomicBoolean();
        JavaPluginUiResource sibling = cancelled.register(() -> {
            siblingCreated.set(true);
            return () -> {};
        });
        cancelHandle.close();
        queue.drain();
        require(cancelHandle.isClosed(), "pending cancellation failed");
        require(!siblingCreated.get(), "cancelling one handle published another before RUNNING");
        cancelled.activate();
        queue.drain();
        require(siblingCreated.get() && sibling.isActive(), "pending sibling failed to activate");
        sibling.close();
        queue.drain();

        JavaPluginUiResources late = createRegistry(queue, active::get, errors::add);
        calls.clear();
        JavaPluginUiResource lateHandle = late.register(() -> {
            active.set(false);
            return () -> calls.add("late close");
        });
        late.activate();
        queue.drain();
        require(lateHandle.isClosed() && calls.size() == 1, "late factory published or leaked");

        active.set(true);
        JavaPluginUiResources broken = createRegistry(queue, active::get, errors::add);
        int beforeErrors = errors.size();
        JavaPluginUiResource brokenHandle = broken.register(() -> {
            throw new IllegalStateException("factory failure");
        });
        JavaPluginUiResource nullHandle = broken.register(() -> null);
        broken.activate();
        queue.drain();
        require(brokenHandle.isClosed() && nullHandle.isClosed(), "failed factory left pending handle");
        require(errors.size() == beforeErrors + 2, "factory errors missing");

        JavaPluginUiResources bounded = createRegistry(queue, active::get, errors::add);

        for (int i = 0; i < 128; i++) {
            bounded.register(() -> () -> {});
        }

        rejects(() -> bounded.register(() -> () -> {}));
        bounded.closeAsync(error -> require(error == null, "unpublished cleanup"));
        queue.drain();
        rejects(() -> bounded.register(() -> () -> {}));
        checkStopGate(queue);
        checkBatchedDispatch(queue);
        checkConcurrentCreation();
    }

    private static void checkBatchedDispatch(Queue queue) {
        List<Integer> created = new ArrayList<>();
        List<Integer> closed = new ArrayList<>();
        JavaPluginUiResources registry = createRegistry(queue, () -> true, error -> {});

        for (int i = 0; i < 20; i++) {
            int index = i;
            registry.register(() -> {
                created.add(index);
                return () -> closed.add(index);
            });
        }

        registry.activate();
        queue.ui = true;
        queue.actions.remove().run();
        queue.ui = false;
        require(created.size() == 8 && queue.actions.size() == 1, "creation did not yield");
        queue.drain();
        require(created.size() == 20, "creation batches lost work");
        AtomicBoolean acknowledged = new AtomicBoolean();
        registry.closeAsync(error -> acknowledged.set(true));
        queue.ui = true;
        queue.actions.remove().run();
        queue.ui = false;
        require(closed.size() == 8 && !acknowledged.get(), "cleanup acknowledged a partial batch");
        queue.drain();
        require(acknowledged.get() && closed.size() == 20, "cleanup batches lost work");

        for (int i = 0; i < 20; i++) {
            require(closed.get(i) == 19 - i, "batch cleanup was not LIFO");
        }
    }

    private static void checkStopGate(Queue queue) throws Exception {
        JavaPluginUiResources registry = createRegistry(queue, () -> true, error -> {});
        ArrayDeque<Runnable> worker = new ArrayDeque<>();
        JavaPluginUiStop stop = new JavaPluginUiStop(registry, worker::add);
        List<String> resumed = new ArrayList<>();
        registry.register(() -> () -> {});
        stop.activate();
        queue.drain();
        require(!stop.prepare(1, () -> resumed.add("reconcile")), "stop did not suspend");
        rejects(stop::requireSuccess);
        require(!stop.prepare(2, () -> resumed.add("removal")), "pending removal did not suspend");
        require(queue.actions.size() == 1, "duplicate close dispatch");
        queue.drain();
        require(resumed.isEmpty() && worker.size() == 1, "main thread executed worker continuation");
        worker.remove().run();
        require(resumed.equals(Arrays.asList("removal")), "pending removal continuation lost");
        require(!stop.prepare(2, () -> resumed.add("ready")), "new generation skipped close attempt");
        queue.drain();
        worker.remove().run();
        require(stop.prepare(2, () -> {}), "acknowledged request not ready");
        stop.requireSuccess();

        JavaPluginUiResources failures = createRegistry(queue, () -> true, error -> {});
        AtomicBoolean failOnce = new AtomicBoolean(true);
        failures.register(() -> () -> {
            if (failOnce.getAndSet(false)) {
                throw new Exception("retry me");
            }
        });
        JavaPluginUiStop gate = new JavaPluginUiStop(failures, worker::add);
        gate.activate();
        queue.drain();
        Object firstRemoval = new Object();
        gate.prepare(firstRemoval, () -> {});
        queue.drain();
        worker.remove().run();
        require(gate.prepare(firstRemoval, () -> {}), "failed attempt did not complete");
        rejects(gate::requireSuccess);
        require(queue.actions.isEmpty(), "failed attempt auto-retried");
        gate.prepare(new Object(), () -> {});
        queue.drain();
        worker.remove().run();
        gate.requireSuccess();
    }

    private static void checkConcurrentCreation() throws Exception {
        ArrayDeque<Runnable> actions = new ArrayDeque<>();
        AtomicReference<Thread> ui = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean closed = new AtomicBoolean();
        JavaPluginUiResources registry = createRegistry(new JavaPluginUiResources.Dispatcher() {
            @Override
            public void post(Runnable action) {
                synchronized (actions) {
                    actions.add(action);
                }
            }

            @Override
            public boolean isUiThread() {
                return Thread.currentThread() == ui.get();
            }
        }, active::get, failure::set);
        registry.register(() -> {
            entered.countDown();
            require(resume.await(5, TimeUnit.SECONDS), "worker blocked by factory monitor");
            return () -> closed.set(true);
        });
        registry.activate();
        Thread thread = new Thread(() -> {
            ui.set(Thread.currentThread());

            try {
                Runnable action;

                synchronized (actions) {
                    action = actions.remove();
                }

                action.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        thread.start();
        require(entered.await(5, TimeUnit.SECONDS), "factory not entered");
        active.set(false);
        registry.closeAsync(error -> require(error == null, "late close failed"));
        resume.countDown();
        thread.join(6000);
        require(!thread.isAlive() && failure.get() == null && closed.get(), "late factory cleanup race");
    }

    private static JavaPluginUiResources createRegistry(
            JavaPluginUiResources.Dispatcher dispatcher,
            BooleanSupplier active,
            Consumer<Throwable> report) {
        PluginSession session = new PluginSession("test.ui", 1, active, () -> {});
        return new JavaPluginUiResources(dispatcher, session.openExternalScope("ui"), report);
    }

    private static void rejects(AutoCloseable action) throws Exception {
        try {
            action.close();
        } catch (Exception expected) {
            return;
        }

        throw new AssertionError("Expected rejection");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
