package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PluginScopeCheck {
    public static void main(String[] args) throws Exception {
        checkNestedRetryAndGeneration();
        checkExternalAcknowledgement();
    }

    private static void checkNestedRetryAndGeneration() throws Exception {
        AtomicBoolean current = new AtomicBoolean(true);
        List<String> closed = new ArrayList<>();
        PluginSession session = new PluginSession("test.scope", 7, current::get, () -> {});
        PluginScope first = session.openScope("first", () -> {}, 4);
        PluginScope nested = first.openChild("nested", () -> {}, 4);
        PluginScope second = session.openScope("second", () -> {}, 4);
        AtomicBoolean failOnce = new AtomicBoolean(true);

        first.registerResource(() -> closed.add("first"));
        nested.registerResource(() -> {
            closed.add("retry");

            if (failOnce.getAndSet(false)) {
                throw new Exception("retry");
            }
        });
        nested.registerResource(() -> closed.add("nested"));
        second.registerResource(() -> closed.add("second"));

        current.set(false);
        require(!first.isActive() && !nested.isActive() && !second.isActive(),
                "generation invalidation did not reach nested scopes");
        rejects(() -> first.registerResource(() -> {}));
        rejects(session::close);
        require(closed.equals(Arrays.asList("second", "nested", "retry", "first")),
                "nested cleanup order or partial progress: " + closed);

        session.close();
        session.close();
        require(closed.equals(Arrays.asList("second", "nested", "retry", "first", "retry")),
                "successful cleanup repeated during retry: " + closed);
    }

    private static void checkExternalAcknowledgement() throws Exception {
        List<String> closed = new ArrayList<>();
        PluginSession session = new PluginSession("test.external", 8, () -> true, () -> {});
        PluginScope worker = session.openScope("worker", () -> {}, 1);
        PluginScope ui = session.openExternalScope("ui");
        worker.registerResource(() -> closed.add("worker"));

        rejects(session::close);
        require(closed.equals(Arrays.asList("worker")), "worker cleanup was blocked by UI scope");
        require(!ui.isActive(), "external scope remained active after session close began");
        ui.finishExternalClose();
        session.close();
        require(closed.equals(Arrays.asList("worker")), "worker cleanup repeated after UI acknowledgement");
    }

    private static void rejects(AutoCloseable action) throws Exception {
        try {
            action.close();
        } catch (Exception expected) {
            return;
        }

        throw new AssertionError("Expected rejection");
    }

    private static void require(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
    }
}
