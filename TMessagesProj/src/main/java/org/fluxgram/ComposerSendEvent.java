package org.fluxgram;

/** Immutable send event with an optional field-local target for the visible Send control. */
public final class ComposerSendEvent {
    public final long sequence;
    public final boolean hasTarget;
    public final float targetX;
    public final float targetY;

    /**
     * Creates a surface-local event. Target coordinates are physical pixels and are meaningful
     * only when {@code hasTarget} is {@code true}.
     */
    public ComposerSendEvent(long sequence, boolean hasTarget, float targetX, float targetY) {
        this.sequence = sequence;
        this.hasTarget = hasTarget;
        this.targetX = targetX;
        this.targetY = targetY;
    }
}
