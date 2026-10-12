package org.telegram.messenger.plugins.api;

public final class ComposerMotionSnapshot {
    public static final int SOURCE_GRAVITY = 1;
    public static final int SOURCE_ACCELEROMETER = 2;

    public final int source;
    public final long timestampNanos;
    public final int accuracy;
    public final float x;
    public final float y;
    public final float z;

    public ComposerMotionSnapshot(
            int source, long timestampNanos, int accuracy, float x, float y, float z) {
        this.source = source;
        this.timestampNanos = timestampNanos;
        this.accuracy = accuracy;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
