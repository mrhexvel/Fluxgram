package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

final class PluginResourceRegistry {
    interface ThreadGuard {
        void requireThread();
    }

    private final BooleanSupplier active;
    private final ThreadGuard threadGuard;
    private final int limit;
    private final ArrayList<Resource> resources = new ArrayList<>();

    PluginResourceRegistry(BooleanSupplier active, ThreadGuard threadGuard, int limit) {
        this.active = active;
        this.threadGuard = threadGuard;
        this.limit = limit;
    }

    AutoCloseable register(AutoCloseable resource) {
        threadGuard.requireThread();

        if (!active.getAsBoolean()) {
            throw new IllegalStateException("Plugin scope is inactive");
        }

        if (resource == null) {
            throw new IllegalArgumentException("Resource is required");
        }

        for (Resource existing : resources) {
            if (existing.delegate == resource) {
                return existing;
            }
        }

        if (resources.size() >= limit) {
            throw new IllegalStateException("Plugin scope resource limit reached");
        }

        Resource handle = new Resource(resource);
        resources.add(0, handle);
        return handle;
    }

    void close() throws Exception {
        threadGuard.requireThread();
        Exception failure = null;

        for (Resource resource : new ArrayList<>(resources)) {
            try {
                resource.close();
            } catch (Exception | LinkageError | AssertionError error) {
                failure = collect(failure, error);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    int size() {
        threadGuard.requireThread();
        return resources.size();
    }

    private static Exception collect(Exception failure, Throwable error) {
        if (failure == null) {
            return new Exception("Plugin resource cleanup failed", error);
        }

        failure.addSuppressed(error);
        return failure;
    }

    private final class Resource implements AutoCloseable {
        private AutoCloseable delegate;
        private boolean closing;

        Resource(AutoCloseable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() throws Exception {
            threadGuard.requireThread();

            if (delegate == null || closing) {
                return;
            }

            closing = true;

            try {
                delegate.close();
                delegate = null;
                resources.remove(this);
            } finally {
                closing = false;
            }
        }
    }
}
