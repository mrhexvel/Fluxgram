package org.fluxgram;

public final class ComposerSendEvent {
    public final long sequence;
    public final boolean hasTarget;
    public final float targetX;
    public final float targetY;

    public ComposerSendEvent(long sequence, boolean hasTarget, float targetX, float targetY) {
        this.sequence = sequence;
        this.hasTarget = hasTarget;
        this.targetX = targetX;
        this.targetY = targetY;
    }
}
