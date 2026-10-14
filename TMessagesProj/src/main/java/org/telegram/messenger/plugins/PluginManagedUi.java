package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class PluginManagedUi {
    private static final int RESOURCE_LIMIT = 32;
    private static final int CHILD_LIMIT = 32;
    private static final AtomicLong nextIdentity = new AtomicLong(1);

    private PluginManagedUi() {
    }

    public static long nextIdentity() {
        return nextIdentity.getAndIncrement();
    }

    public static Scope openActivity(int account, long ownerId, long identity,
            BooleanSupplier active) {
        if (account < 0 || ownerId <= 0 || identity <= 0 || active == null) {
            throw new IllegalArgumentException("Managed UI activity identity is invalid");
        }

        return new Scope(null, Kind.ACTIVITY, account, ownerId, identity,
                Thread.currentThread(), active);
    }

    private enum Kind {
        ACTIVITY,
        FRAGMENT,
        VIEW,
        SURFACE
    }

    public static final class Scope implements AutoCloseable {
        private final Scope parent;
        private final Kind kind;
        private final int account;
        private final long ownerId;
        private final long identity;
        private final Thread thread;
        private final BooleanSupplier active;
        private final PluginResourceRegistry resources;
        private final ArrayList<Scope> children = new ArrayList<>();
        private boolean closing;
        private boolean closed;
        private boolean closeInProgress;

        private Scope(Scope parent, Kind kind, int account, long ownerId,
                long identity, Thread thread, BooleanSupplier active) {
            this.parent = parent;
            this.kind = kind;
            this.account = account;
            this.ownerId = ownerId;
            this.identity = identity;
            this.thread = thread;
            this.active = active;
            resources = new PluginResourceRegistry(this::isActive, this::requireThread, RESOURCE_LIMIT);
        }

        public Scope openFragment(long fragmentIdentity, BooleanSupplier fragmentActive) {
            return openChild(Kind.ACTIVITY, Kind.FRAGMENT, fragmentIdentity, fragmentActive);
        }

        public Scope openView(long viewIdentity, BooleanSupplier viewActive) {
            return openChild(Kind.FRAGMENT, Kind.VIEW, viewIdentity, viewActive);
        }

        public Scope openSurface(long surfaceIdentity, BooleanSupplier surfaceActive) {
            return openChild(Kind.VIEW, Kind.SURFACE, surfaceIdentity, surfaceActive);
        }

        public AutoCloseable register(AutoCloseable resource) {
            requireThread();
            return resources.register(resource);
        }

        public boolean isActive() {
            requireThread();
            return !closing && !closed && active.getAsBoolean()
                    && (parent == null || parent.isActive());
        }

        public int getAccount() {
            return account;
        }

        public long getOwnerId() {
            return ownerId;
        }

        public long getIdentity() {
            return identity;
        }

        private Scope openChild(Kind requiredKind, Kind childKind,
                long childIdentity, BooleanSupplier childActive) {
            requireThread();

            if (kind != requiredKind || childIdentity <= 0 || childActive == null) {
                throw new IllegalArgumentException("Managed UI child identity is invalid");
            }

            if (!isActive()) {
                throw new IllegalStateException("Managed UI scope is inactive");
            }

            if (children.size() >= CHILD_LIMIT) {
                throw new IllegalStateException("Managed UI child limit reached");
            }

            Scope child = new Scope(this, childKind, account, ownerId,
                    childIdentity, thread, childActive);
            children.add(0, child);
            return child;
        }

        private void beginClose() {
            requireThread();
            closing = true;

            for (Scope child : new ArrayList<>(children)) {
                child.beginClose();
            }
        }

        @Override
        public void close() throws Exception {
            requireThread();

            if (closed) {
                return;
            }

            if (closeInProgress) {
                throw new IllegalStateException("Managed UI cleanup is already running");
            }

            closeInProgress = true;

            try {
                beginClose();
                Exception failure = null;

                for (Scope child : new ArrayList<>(children)) {
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

                if (!children.isEmpty()) {
                    throw new IllegalStateException("Managed UI scope still has children");
                }

                closed = true;

                if (parent != null) {
                    parent.children.remove(this);
                }
            } finally {
                closeInProgress = false;
            }
        }

        private void requireThread() {
            if (Thread.currentThread() != thread) {
                throw new IllegalStateException("Managed UI scope requires its owner thread");
            }
        }
    }

    private static Exception collect(Exception failure, Throwable error) {
        if (failure == null) {
            return new Exception("Managed UI cleanup failed", error);
        }

        failure.addSuppressed(error);
        return failure;
    }
}
