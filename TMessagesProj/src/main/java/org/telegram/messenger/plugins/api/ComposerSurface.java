package org.telegram.messenger.plugins.api;

public interface ComposerSurface {
    int getAccount();

    long getOwnerId();

    long getDialogId();

    ComposerGeometry getGeometry();

    boolean isActive();

    void requestFrame();

    default boolean performHapticFeedback(int feedback) {
        return false;
    }

    default boolean setMotionUpdatesEnabled(boolean enabled) {
        return false;
    }
}
