package org.telegram.messenger.fluxgram;

public final class FolderSwipeGesturePolicy {
    private static final int COMMIT_VELOCITY = 2500;

    private FolderSwipeGesturePolicy() {
    }

    public static boolean shouldStart(float velocityX, float velocityY) {
        return Math.abs(velocityX) >= COMMIT_VELOCITY
                && Math.abs(velocityX) > Math.abs(velocityY);
    }

    public static boolean shouldCommit(
            float distanceX,
            int width,
            float velocityX,
            float velocityY,
            boolean forward
    ) {
        if (width <= 0) {
            return false;
        }
        boolean velocityCommit = shouldStart(velocityX, velocityY)
                && (forward ? velocityX < 0 : velocityX > 0);
        if (distanceX == 0) {
            return velocityCommit;
        }

        boolean distanceCommit = forward ? distanceX < 0 : distanceX > 0;
        boolean velocityReversed = shouldStart(velocityX, velocityY) && !velocityCommit;
        return distanceCommit && !velocityReversed;
    }
}
