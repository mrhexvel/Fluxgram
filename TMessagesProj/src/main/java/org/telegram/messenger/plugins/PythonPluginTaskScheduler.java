package org.telegram.messenger.plugins;

import com.chaquo.python.PyObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.ForegroundDetector;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;

final class PythonPluginTaskScheduler implements ForegroundDetector.Listener {
    interface Host {
        boolean isGenerationActive(long generation);

        boolean enqueue(Runnable task);
    }

    static final int MAX_TASKS = 128;

    private final String pluginId;
    private final Host host;
    private final LinkedHashMap<Key, Record> records = new LinkedHashMap<>();
    private final ArrayDeque<Record> ready = new ArrayDeque<>();
    private boolean drainQueued;
    private boolean listenerRegistered;
    private boolean foreground;

    PythonPluginTaskScheduler(String pluginId, Host host) {
        this.pluginId = pluginId;
        this.host = host;
        ForegroundDetector detector = ForegroundDetector.getInstance();
        foreground = detector != null && detector.isForeground();
    }

    synchronized int schedule(
            long generation,
            long taskId,
            long delayMs,
            long intervalMs,
            PyObject callback,
            PyObject completion) {
        if (!host.isGenerationActive(generation)) {
            return 2;
        }

        if (callback == null
                || taskId <= 0
                || delayMs < 0
                || intervalMs < 0
                || records.size() >= MAX_TASKS) {
            return 1;
        }

        Key key = new Key(generation, taskId);

        if (records.containsKey(key)) {
            return 1;
        }

        Record record = new Record(key, delayMs, intervalMs, callback, completion);
        records.put(key, record);

        if (record.periodic()) {
            updateForegroundListenerLocked();

            if (!foreground) {
                record.paused = true;
                return 0;
            }
        }

        if (!scheduleLocked(record, delayMs)) {
            cancelLocked(record);
            return host.isGenerationActive(generation) ? 1 : 2;
        }

        return 0;
    }

    synchronized void cancel(long generation, long taskId) {
        Record record = records.get(new Key(generation, taskId));

        if (record != null) {
            cancelLocked(record);
        }
    }

    synchronized void cancelGeneration(long generation) {
        for (Record record : records.values().toArray(new Record[0])) {
            if (record.key.generation == generation) {
                cancelLocked(record);
            }
        }
    }

    synchronized void cancelAll() {
        for (Record record : records.values().toArray(new Record[0])) {
            cancelLocked(record);
        }
    }

    synchronized int activeCount() {
        return records.size();
    }

    synchronized boolean isActive(long generation, long taskId) {
        return records.containsKey(new Key(generation, taskId));
    }

    private boolean scheduleLocked(Record record, long delayMs) {
        record.paused = false;

        if (delayMs == 0) {
            return makeReadyLocked(record);
        }

        Runnable timer = () -> timerFired(record);
        record.timer = timer;
        AndroidUtilities.runOnUIThread(timer, delayMs);
        return true;
    }

    private void timerFired(Record record) {
        synchronized (this) {
            if (records.get(record.key) != record || record.cancelled) {
                return;
            }

            record.timer = null;

            if (!host.isGenerationActive(record.key.generation)) {
                cancelLocked(record);
                return;
            }

            if (record.periodic() && !foreground) {
                record.paused = true;
                return;
            }

            if (!makeReadyLocked(record)) {
                cancelLocked(record);
            }
        }
    }

    private boolean makeReadyLocked(Record record) {
        if (record.ready || record.running || record.cancelled) {
            return false;
        }

        record.ready = true;
        ready.addLast(record);
        return requestDrainLocked();
    }

    private boolean requestDrainLocked() {
        if (drainQueued || ready.isEmpty()) {
            return true;
        }

        drainQueued = true;

        if (host.enqueue(this::drainOne)) {
            return true;
        }

        drainQueued = false;
        return false;
    }

    private void drainOne() {
        Record record = null;

        synchronized (this) {
            drainQueued = false;

            while (!ready.isEmpty()) {
                Record candidate = ready.removeFirst();
                candidate.ready = false;

                if (records.get(candidate.key) == candidate && !candidate.cancelled) {
                    record = candidate;
                    record.running = true;
                    break;
                }
            }
        }

        if (record == null) {
            return;
        }

        boolean keep = false;
        PyObject callback = record.callback;

        try {
            if (host.isGenerationActive(record.key.generation) && callback != null) {
                keep = Boolean.TRUE.equals(callback.call().toJava(Boolean.class));
            }
        } catch (Exception | LinkageError error) {
            FileLog.e("[" + pluginId + "] Managed task failed", error);
        }

        synchronized (this) {
            record.running = false;

            if (records.get(record.key) == record && !record.cancelled) {
                if (record.periodic()
                        && keep
                        && host.isGenerationActive(record.key.generation)) {
                    record.resumeDelayMs = record.intervalMs;

                    if (foreground) {
                        if (!scheduleLocked(record, record.intervalMs)) {
                            cancelLocked(record);
                        }
                    } else {
                        record.paused = true;
                    }
                } else if (!record.periodic()
                        && record.completion != null
                        && host.isGenerationActive(record.key.generation)) {
                    record.callback = record.completion;
                    record.completion = null;

                    if (!makeReadyLocked(record)) {
                        cancelLocked(record);
                    }
                } else {
                    cancelLocked(record);
                }
            }

            if (!requestDrainLocked()) {
                cancelReadyLocked();
            }
        }
    }

    private void cancelLocked(Record record) {
        if (records.get(record.key) != record) {
            return;
        }

        records.remove(record.key);
        record.cancelled = true;
        record.callback = null;
        record.completion = null;

        if (record.timer != null) {
            AndroidUtilities.cancelRunOnUIThread(record.timer);
            record.timer = null;
        }

        if (record.ready) {
            ready.remove(record);
            record.ready = false;
        }

        updateForegroundListenerLocked();
    }

    private void cancelReadyLocked() {
        while (!ready.isEmpty()) {
            cancelLocked(ready.removeFirst());
        }
    }

    private void updateForegroundListenerLocked() {
        boolean required = false;

        for (Record record : records.values()) {
            if (record.periodic()) {
                required = true;
                break;
            }
        }

        ForegroundDetector detector = ForegroundDetector.getInstance();

        if (required && !listenerRegistered && detector != null) {
            foreground = detector.isForeground();
            detector.addListener(this);
            listenerRegistered = true;
        } else if (!required && listenerRegistered && detector != null) {
            detector.removeListener(this);
            listenerRegistered = false;
        }
    }

    @Override
    public synchronized void onBecameForeground() {
        foreground = true;

        for (Record record : records.values()) {
            if (record.periodic() && record.paused && !record.running && !record.ready) {
                scheduleLocked(record, record.resumeDelayMs);
            }
        }
    }

    @Override
    public synchronized void onBecameBackground() {
        foreground = false;

        for (Record record : records.values()) {
            if (!record.periodic() || record.running) {
                continue;
            }

            if (record.timer != null) {
                AndroidUtilities.cancelRunOnUIThread(record.timer);
                record.timer = null;
            }

            if (record.ready) {
                ready.remove(record);
                record.ready = false;
            }

            record.paused = true;
        }
    }

    private static final class Key {
        final long generation;
        final long taskId;

        Key(long generation, long taskId) {
            this.generation = generation;
            this.taskId = taskId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof Key)) {
                return false;
            }

            Key key = (Key) other;
            return generation == key.generation && taskId == key.taskId;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(generation) + Long.hashCode(taskId);
        }
    }

    private static final class Record {
        final Key key;
        final long intervalMs;
        long resumeDelayMs;
        PyObject callback;
        PyObject completion;
        Runnable timer;
        boolean ready;
        boolean running;
        boolean paused;
        boolean cancelled;

        Record(
                Key key,
                long delayMs,
                long intervalMs,
                PyObject callback,
                PyObject completion) {
            this.key = key;
            this.intervalMs = intervalMs;
            resumeDelayMs = delayMs;
            this.callback = callback;
            this.completion = completion;
        }

        boolean periodic() {
            return intervalMs > 0;
        }
    }
}
