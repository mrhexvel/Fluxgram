package org.telegram.messenger.plugins;

import java.util.ArrayList;

final class PluginScope implements AutoCloseable {
    private final PluginSession session;
    private final PluginScope parent;
    private final String name;
    private final boolean externallyManaged;
    private final PluginResourceRegistry resources;
    private final ArrayList<PluginScope> children = new ArrayList<>();
    private volatile boolean closing;
    private volatile boolean closed;

    PluginScope(PluginSession session, PluginScope parent, String name,
            PluginResourceRegistry.ThreadGuard threadGuard, int resourceLimit,
            boolean externallyManaged) {
        this.session = session;
        this.parent = parent;
        this.name = name;
        this.externallyManaged = externallyManaged;
        resources = externallyManaged
                ? null
                : new PluginResourceRegistry(this::isActive, threadGuard, resourceLimit);
    }

    boolean isActive() {
        return !closing && !closed && session.isCurrent()
                && (parent == null || parent.isActive());
    }

    synchronized PluginScope openChild(String childName,
            PluginResourceRegistry.ThreadGuard threadGuard, int resourceLimit) {
        requireOpen();
        PluginScope child = new PluginScope(
                session, this, childName, threadGuard, resourceLimit, false);
        children.add(0, child);
        return child;
    }

    synchronized PluginScope openExternalChild(String childName) {
        requireOpen();
        PluginScope child = new PluginScope(
                session, this, childName, () -> {}, 0, true);
        children.add(0, child);
        return child;
    }

    AutoCloseable registerResource(AutoCloseable resource) {
        if (resources == null) {
            throw new IllegalStateException("Externally managed scope cannot register resources");
        }

        return resources.register(resource);
    }

    void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Plugin scope is inactive: " + name);
        }
    }

    private void requireOpen() {
        if (closing || closed || (parent != null && !parent.isOpen())) {
            throw new IllegalStateException("Plugin scope is closed: " + name);
        }
    }

    private boolean isOpen() {
        return !closing && !closed && (parent == null || parent.isOpen());
    }

    void beginClose() {
        ArrayList<PluginScope> snapshot;

        synchronized (this) {
            closing = true;
            snapshot = new ArrayList<>(children);
        }

        for (PluginScope child : snapshot) {
            child.beginClose();
        }
    }

    void finishExternalClose() {
        if (!externallyManaged) {
            throw new IllegalStateException("Plugin scope owns its cleanup: " + name);
        }

        finishClose();
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }

        beginClose();

        if (externallyManaged) {
            throw new IllegalStateException("Plugin scope requires external cleanup: " + name);
        }

        Exception failure = null;
        ArrayList<PluginScope> snapshot;

        synchronized (this) {
            snapshot = new ArrayList<>(children);
        }

        for (PluginScope child : snapshot) {
            try {
                child.close();
            } catch (Exception | LinkageError | AssertionError error) {
                failure = collect(failure, error);
            }
        }

        try {
            resources.close();
        } catch (Exception | LinkageError | AssertionError error) {
            failure = collect(failure, error);
        }

        if (failure != null) {
            throw failure;
        }

        finishClose();
    }

    private void finishClose() {
        synchronized (this) {
            if (closed) {
                return;
            }

            if (!children.isEmpty()) {
                throw new IllegalStateException("Plugin scope still has children: " + name);
            }

            closing = true;
            closed = true;
        }

        if (parent != null) {
            parent.removeChild(this);
        }
    }

    private synchronized void removeChild(PluginScope child) {
        children.remove(child);
    }

    private static Exception collect(Exception failure, Throwable error) {
        if (failure == null) {
            return new Exception("Plugin scope cleanup failed", error);
        }

        failure.addSuppressed(error);
        return failure;
    }
}
