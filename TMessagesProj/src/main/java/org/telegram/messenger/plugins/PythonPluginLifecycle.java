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

        return false;
    }

    synchronized void cancelReconcile() {
        reconcileQueued = false;
    }

    synchronized boolean isCurrent(long request) {
        return !removal && enabled && generation == request;
    }

    synchronized boolean fail(long request) {
        if (removal || generation != request) {
            return false;
        }

        enabled = false;
        state = State.FAILED;

        return true;
    }

    synchronized boolean setState(long request, State value) {
        if (removal || generation != request) {
            return false;
        }

        state = value;

        return true;
    }

    synchronized void beginRemoval() {
        if (!removal) {
            enabled = false;
            generation++;
            removal = true;
        }

        state = State.REMOVAL_PENDING;
    }

    synchronized void failRemoval() {
        state = State.FAILED;
    }

    synchronized void terminate() {
        enabled = false;
        removal = true;
        reconcileQueued = false;
        state = State.TERMINATED;
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
}
