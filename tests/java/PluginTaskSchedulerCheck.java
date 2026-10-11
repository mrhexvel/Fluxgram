package org.telegram.messenger.plugins;

import com.chaquo.python.PyObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.ForegroundDetector;

import java.util.ArrayDeque;

public final class PluginTaskSchedulerCheck {
    private static final class Host implements PythonPluginTaskScheduler.Host {
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        long generation = 1;

        @Override
        public boolean isGenerationActive(long value) {
            return generation == value;
        }

        @Override
        public boolean enqueue(Runnable task) {
            queue.addLast(task);
            return true;
        }

        void runNext() {
            Runnable task = queue.pollFirst();

            if (task == null) {
                throw new AssertionError("Missing queued task");
            }

            task.run();
        }
    }

    public static void main(String[] args) {
        ForegroundDetector detector = new ForegroundDetector();
        Host host = new Host();
        PythonPluginTaskScheduler scheduler = new PythonPluginTaskScheduler("test.plugin", host);
        int[] calls = {0};

        check(scheduler.schedule(1, 1, 0, 0, callback(calls), null) == 0);
        check(scheduler.schedule(1, 2, 0, 0, callback(calls), null) == 0);
        check(host.queue.size() == 1);
        host.runNext();
        check(calls[0] == 1 && host.queue.size() == 1);
        host.runNext();
        check(calls[0] == 2 && scheduler.activeCount() == 0);

        check(scheduler.schedule(1, 3, 1000, 0, callback(calls), null) == 0);
        scheduler.cancel(1, 3);
        AndroidUtilities.advance(1000);
        check(host.queue.isEmpty() && calls[0] == 2);

        check(scheduler.schedule(1, 4, 1000, 1000, callback(calls), null) == 0);
        detector.background();
        AndroidUtilities.advance(5000);
        check(host.queue.isEmpty());
        detector.foreground();
        AndroidUtilities.advance(999);
        check(host.queue.isEmpty());
        AndroidUtilities.advance(1);
        host.runNext();
        check(calls[0] == 3);
        detector.background();
        AndroidUtilities.advance(5000);
        check(host.queue.isEmpty());

        detector.foreground();
        check(scheduler.schedule(1, 5, 1000, 0, callback(calls), null) == 0);
        host.generation = 2;
        AndroidUtilities.advance(1000);
        check(host.queue.isEmpty() && calls[0] == 3);
        scheduler.cancelAll();

        for (int index = 0; index < PythonPluginTaskScheduler.MAX_TASKS; index++) {
            check(scheduler.schedule(2, 100 + index, 60000, 0, callback(calls), null) == 0);
        }

        check(scheduler.schedule(2, 999, 60000, 0, callback(calls), null) == 1);
        scheduler.cancelAll();
        check(scheduler.activeCount() == 0);

        int[] completions = {0};
        check(scheduler.schedule(
                2,
                1000,
                0,
                0,
                callback(calls),
                callback(completions)) == 0);
        host.runNext();
        scheduler.cancel(2, 1000);
        host.runNext();
        check(completions[0] == 0 && scheduler.activeCount() == 0);

        detector.background();
        check(scheduler.schedule(2, 1001, 0, 1000, callback(calls), null) == 0);
        detector.foreground();
        check(host.queue.size() == 1);
        host.runNext();
        check(calls[0] == 5);
        scheduler.cancelAll();
    }

    private static PyObject callback(int[] calls) {
        return new PyObject(() -> {
            calls[0]++;
            return true;
        });
    }

    private static void check(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }
}
