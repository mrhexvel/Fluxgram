package android.view.animation;

import android.view.Choreographer;

public final class AnimationUtils {
    public static long currentAnimationTimeMillis() {
        return Choreographer.getInstance().getFrameTimeNanos() / 1_000_000;
    }
}
