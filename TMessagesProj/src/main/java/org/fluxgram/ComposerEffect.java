package org.fluxgram;

import android.graphics.Canvas;

/**
 * A main-thread visual effect attached to one active chat composer surface.
 *
 * <p>The host closes the instance when that surface becomes ineligible or is destroyed. A factory
 * or callback exception quarantines the registration. Three callbacks exceeding the 4 ms
 * diagnostic budget also quarantine it; the budget does not interrupt a running callback.
 * Continuous {@link #onFrame(long)} demand without new input is bounded to two seconds. Failed
 * cleanup is retried, so {@link #close()} implementations must be idempotent.</p>
 */
public interface ComposerEffect extends AutoCloseable {
    /** Creates one effect instance for each eligible surface on the main thread. */
    interface Factory {
        /**
         * Creates an effect owned by {@code surface}.
         *
         * @param surface lifecycle-scoped surface that remains valid until the effect is closed
         */
        ComposerEffect create(ComposerSurface surface) throws Exception;
    }

    /** Receives coalesced counts-only input on the main thread before the next frame callback. */
    void onInput(ComposerInput input) throws Exception;

    /**
     * Advances animation state on the main thread at the supplied vsync time.
     *
     * @param frameTimeNanos monotonic frame timestamp in nanoseconds
     * @return {@code true} to request another frame, or {@code false} to stop continuous updates
     */
    boolean onFrame(long frameTimeNanos) throws Exception;

    /**
     * Draws an additive layer in field-local pixel coordinates on the main thread.
     * The canvas is callback-scoped, clipped to the composer field, and must not be retained.
     */
    void draw(Canvas canvas, ComposerGeometry geometry) throws Exception;

    /** Releases effect-owned resources on the main thread. */
    @Override
    void close() throws Exception;
}
