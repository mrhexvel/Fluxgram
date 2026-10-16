package org.telegram.messenger.plugins;

final class PythonPluginLifecycle {
    enum State {
        DISABLED,
        STARTING,
        RUNNING,
        STOPPING,
        FAILED,
        REMOVAL_PENDING,
        TERMINATED
    }

    static final class Request {
        final long generation;
        final boolean enabled;
        final boolean removal;

        Request(long generation, boolean enabled, boolean removal) {
            this.generation = generation;
            this.enabled = enabled;
            this.removal = removal;
        }
    }

    private long generation;
    private boolean enabled;
    private boolean removal;
    private boolean reconcileQueued;
    private State state = State.DISABLED;

    synchronized boolean requestEnabled(boolean value) {
        if (removal || state == State.TERMINATED) {
            return false;
        }

        enabled = value;
        generation++;
        notifyAll();

        if (reconcileQueued) {
            return false;
        }

        reconcileQueued = true;

        return true;
    }

    synchronized Request currentRequest() {
        return new Request(generation, enabled, removal);
    }

    synchronized boolean finishReconcile(long request) {
        if (!removal && request != generation) {
            return true;
        }

        reconcileQueued = false;
        notifyAll();

        return false;
    }

    synchronized void cancelReconcile() {
        reconcileQueued = false;
        notifyAll();
    }

    synchronized boolean isCurrent(long request) {
        return !removal && enabled && generation == request;
    }

    synchronized boolean isRunning(long request) {
        return !removal && enabled && generation == request && state == State.RUNNING;
    }

    synchronized boolean commitIfRunning(long request, Runnable operation) {
        if (!isRunning(request)) {
            return false;
        }

        operation.run();
        return true;
    }

    synchronized boolean fail(long request) {
        if (removal || generation != request) {
            return false;
        }

        enabled = false;
        state = State.FAILED;
        notifyAll();

        return true;
    }

    synchronized boolean setState(long request, State value) {
        if (removal || generation != request) {
            return false;
        }

        state = value;
        notifyAll();

        return true;
    }

    synchronized void beginRemoval() {
        if (!removal) {
            enabled = false;
            generation++;
            removal = true;
        }

        state = State.REMOVAL_PENDING;
        notifyAll();
    }

    synchronized void failRemoval() {
        state = State.FAILED;
        notifyAll();
    }

    synchronized void terminate() {
        enabled = false;
        removal = true;
        reconcileQueued = false;
        state = State.TERMINATED;
        notifyAll();
    }

    synchronized boolean isRemovalPending() {
        return removal;
    }

    synchronized boolean isEnabled() {
        return enabled;
    }

    synchronized long getGeneration() {
        return generation;
    }

    synchronized State getState() {
        return state;
    }

    synchronized State awaitSettled(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (reconcileQueued || state == State.STARTING || state == State.STOPPING) {
            long remaining = deadline - System.currentTimeMillis();

            if (remaining <= 0) {
                return null;
            }

            wait(remaining);
        }

        return state;
    }
}
