package org.fluxgram;

/**
 * Non-blocking handle for a host-managed UI resource.
 *
 * <p>The handle belongs to one plugin generation. Generation invalidation makes it inactive
 * immediately, while {@link #isClosed()} becomes true only after main-thread cleanup has been
 * acknowledged. The host retains failed cleanup for an explicit teardown retry.</p>
 */
public interface JavaPluginUiResource extends AutoCloseable {
    /** Creates the actual UI resource after the plugin reaches its running state. */
    interface Factory {
        /**
         * Creates a UI resource on the Android main thread.
         *
         * @return non-null cleanup handle, whose {@link AutoCloseable#close()} is also invoked on
         *         the main thread
         * @throws Exception if creation fails; partially allocated state remains the factory's
         *                   responsibility
         */
        AutoCloseable create() throws Exception;
    }

    /**
     * Reports whether creation completed and the resource may currently receive work.
     *
     * @return {@code true} only while the delegate exists, the generation is active, and neither
     *         resource nor registry teardown has begun
     */
    boolean isActive();

    /**
     * Reports whether cleanup has completed successfully or a pending factory was cancelled.
     *
     * @return {@code true} after main-thread close acknowledgement
     */
    boolean isClosed();

    /**
     * Requests asynchronous main-thread cleanup.
     *
     * <p>This method is idempotent, non-blocking, and safe to call from any thread. It may return
     * before {@link #isClosed()} becomes true. If delegate cleanup fails, the handle stays not
     * closed so host teardown can retry it.</p>
     */
    @Override
    void close();
}
