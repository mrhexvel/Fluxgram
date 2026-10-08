package org.telegram.messenger.fluxgram;

import android.util.SparseArray;

import org.telegram.messenger.UserConfig;

public final class GhostModeTemporarySettings {
    private static final SparseArray<State> states = new SparseArray<>();

    private static long nextGeneration;

    private GhostModeTemporarySettings() {
    }

    public static synchronized boolean isActive(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        State state = states.get(account);

        if (state != null && state.owner != owner) {
            states.remove(account);
            state = null;
        }

        return owner != 0 && state != null;
    }

    public static synchronized Session acquire(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();

        if (owner == 0) {
            return null;
        }

        State state = states.get(account);

        if (state == null || state.owner != owner) {
            state = new State(owner, ++nextGeneration);
            states.put(account, state);
        }

        state.references++;

        return new Session(account, owner, state.generation);
    }

    public static synchronized void clearAccount(int account) {
        states.remove(account);
    }

    private static synchronized void release(Session session) {
        State state = states.get(session.account);

        if (state == null || state.owner != session.owner || state.generation != session.generation) {
            return;
        }

        state.references--;

        if (state.references == 0) {
            states.remove(session.account);
        }
    }

    private static final class State {
        private final long owner;
        private final long generation;
        private int references;

        private State(long owner, long generation) {
            this.owner = owner;
            this.generation = generation;
        }
    }

    public static final class Session {
        private final int account;
        private final long owner;
        private final long generation;

        private boolean released;

        private Session(int account, long owner, long generation) {
            this.account = account;
            this.owner = owner;
            this.generation = generation;
        }

        public void release() {
            synchronized (GhostModeTemporarySettings.class) {
                if (released) {
                    return;
                }

                released = true;
                GhostModeTemporarySettings.release(this);
            }
        }
    }
}
