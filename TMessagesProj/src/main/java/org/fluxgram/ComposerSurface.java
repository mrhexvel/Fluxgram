package org.fluxgram;

/**
 * Lifecycle-scoped main-thread control surface for one composer effect instance.
 * Calls are accepted only while the corresponding composer and plugin generation are active.
 */
public interface ComposerSurface {
    /** Returns the captured Telegram account slot. */
    int getAccount();

    /** Returns the captured account owner user ID. */
    long getOwnerId();

    /** Returns the captured dialog ID for this surface. */
    long getDialogId();

    /** Returns whether this is a preview surface; stable host surfaces currently return {@code false}. */
    default boolean isPreview() {
        return false;
    }

    /** Returns the latest immutable field geometry, in physical pixels, on the main thread. */
    ComposerGeometry getGeometry();

    /** Returns whether the surface still accepts callbacks and requests. */
    boolean isActive();

    /** Requests a demand-driven main-thread frame; duplicate requests may be coalesced. */
    void requestFrame();

    /**
     * Requests one semantic haptic from the composer field.
     * Requests are system-setting-aware and bounded to eight per second with at least 40 ms spacing.
     */
    default boolean performHapticFeedback(int feedback) {
        return false;
    }

    /** Enables or disables coalesced motion callbacks for a {@link ComposerMotionEffect}. */
    default boolean setMotionUpdatesEnabled(boolean enabled) {
        return false;
    }
}
