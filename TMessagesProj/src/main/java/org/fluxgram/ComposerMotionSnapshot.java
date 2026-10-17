package org.fluxgram;

/** Immutable raw gravity or accelerometer sample; axis interpretation and filtering are plugin policy. */
public final class ComposerMotionSnapshot {
    public static final int SOURCE_GRAVITY = 1;
    public static final int SOURCE_ACCELEROMETER = 2;

    public final int source;
    public final long timestampNanos;
    public final int accuracy;
    public final float x;
    public final float y;
    public final float z;

    /** Creates a raw sample using the platform sensor timestamp in nanoseconds. */
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
