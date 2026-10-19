package org.fluxgram;

import android.content.Context;

/** Screen-scoped services supplied to a {@link JavaSettingsBlock.Factory}. */
public interface JavaSettingsBlockContext {
    int COLOR_BACKGROUND = 0;
    int COLOR_PRIMARY_TEXT = 1;
    int COLOR_SECONDARY_TEXT = 2;
    int COLOR_ACCENT = 3;
    int COLOR_DIVIDER = 4;

    /**
     * Returns the Android context of the current settings screen.
     *
     * @return non-null screen context
     */
    Context getContext();

    /**
     * Resolves a current Telegram theme color.
     *
     * @param role one of the {@code COLOR_*} constants in this interface
     * @return opaque or alpha-bearing ARGB color
     * @throws IllegalArgumentException for an unknown role
     */
    int getColor(int role);

    /**
     * Reports whether the screen binding and plugin generation remain active.
     *
     * @return {@code true} while UI work may still be applied
     */
    boolean isActive();

    /**
     * Enqueues plugin work without blocking the Android main thread.
     *
     * <p>The action runs on the serialized plugin worker only if the screen and generation are
     * still active. Queue saturation rejects the action. The action must not mutate Android views;
     * publish UI work back through the view's normal main-thread facilities after checking
     * {@link #isActive()}.</p>
     *
     * @param action non-null plugin action
     * @return {@code true} when accepted by the bounded plugin queue
     */
    boolean postAction(Runnable action);
}
