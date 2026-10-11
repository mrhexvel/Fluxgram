package android.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public final class Choreographer {
    public interface FrameCallback {
        void doFrame(long timestamp);
    }

    private static final Choreographer INSTANCE = new Choreographer();
    public final LinkedHashSet<FrameCallback> pending = new LinkedHashSet<>();
    private long timestamp;

    public long getFrameTimeNanos() {
        return timestamp;
    }

    public static Choreographer getInstance() {
        return INSTANCE;
    }

    public void postFrameCallback(FrameCallback callback) {
        pending.add(callback);
    }

    public void postFrameCallbackDelayed(FrameCallback callback, long delayMillis) {
        pending.add(callback);
    }

    public void removeFrameCallback(FrameCallback callback) {
        pending.remove(callback);
    }

    public void tick(long timestamp) {
        this.timestamp = timestamp;
        ArrayList<FrameCallback> callbacks = new ArrayList<>(pending);
        pending.clear();

        for (FrameCallback callback : callbacks) {
            callback.doFrame(timestamp);
        }
    }
}
