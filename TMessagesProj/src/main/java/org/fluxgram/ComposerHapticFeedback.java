package org.fluxgram;

/** Semantic haptic feedback types accepted by {@link ComposerSurface#performHapticFeedback(int)}. */
public final class ComposerHapticFeedback {
    public static final int TICK = 1;
    public static final int IMPACT = 2;
    public static final int CONFIRM = 3;
    public static final int REJECT = 4;

    private ComposerHapticFeedback() {
    }
}
