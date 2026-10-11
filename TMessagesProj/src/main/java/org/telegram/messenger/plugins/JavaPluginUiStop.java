package org.telegram.messenger.plugins;

import java.util.function.Consumer;

final class JavaPluginUiStop {
    private final JavaPluginUiResources resources;
    private final Consumer<Runnable> worker;
    private boolean pending;
    private boolean completed;
    private Object request;
    private Exception failure;
    private Runnable continuation;

    JavaPluginUiStop(JavaPluginUiResources resources, Consumer<Runnable> worker) {
        this.resources = resources;
        this.worker = worker;
    }

    void activate() {
        resources.activate();
    }

    boolean prepare(Object attempt, Runnable resume) {
        if (!pending && completed && request.equals(attempt)) {
            return true;
        }

        continuation = resume;

        if (pending) {
            return false;
        }

        request = attempt;
        pending = true;
        completed = false;
        resources.closeAsync(error -> worker.accept(() -> {
            failure = error;
            completed = true;
            pending = false;
            Runnable next = continuation;
            continuation = null;
            next.run();
        }));
        return false;
    }

    void requireSuccess() throws Exception {
        if (pending || !completed) {
            throw new IllegalStateException("UI cleanup has not completed");
        }

        if (failure != null) {
            throw failure;
        }
    }
}
