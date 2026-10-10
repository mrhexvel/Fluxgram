package org.telegram.messenger.plugins;

import java.util.concurrent.LinkedBlockingDeque;

final class PythonPluginTaskQueue extends LinkedBlockingDeque<Runnable> {
    private static final class ControlTask implements Runnable {
        private final Runnable delegate;

        ControlTask(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }
    }

    PythonPluginTaskQueue(int capacity) {
        super(capacity);
    }

    static Runnable control(Runnable task) {
        return new ControlTask(task);
    }

    @Override
    public boolean offer(Runnable task) {
        if (task instanceof ControlTask) {
            return offerFirst(task);
        }

        return offerLast(task);
    }
}
