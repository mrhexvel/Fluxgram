package org.fluxgram;

/**
 * Entrypoint implemented by a Java plugin module.
 *
 * <p>The host constructs one module instance for each runtime generation and invokes all methods
 * sequentially on that plugin's worker thread. A module must have a public no-argument
 * constructor. Throwing from a lifecycle method fails the corresponding host operation; the host
 * still attempts managed-resource cleanup and {@link #onUnload()} when tearing the generation
 * down.</p>
 */
public interface JavaPluginModule {
    /**
     * Starts this module for the supplied active generation.
     *
     * <p>Register resources and callbacks through {@code context}. The context becomes inactive
     * when this generation is disabled, replaced, removed, or fails to start. Retained work must
     * check {@link JavaPluginContext#isActive()} before publishing a result.</p>
     *
     * @param context generation-scoped plugin context; never {@code null}
     * @throws Exception if startup cannot complete
     */
    void onLoad(JavaPluginContext context) throws Exception;

    /**
     * Reports a changed declared setting while this generation remains active.
     *
     * <p>Changes are coalesced by key and stale generation or account-owner deliveries are
     * discarded by the host. The value is the declared setting's boxed value: {@link Boolean},
     * {@link Integer}, or {@link String}. An exception is logged and does not fail or restart the
     * generation; a hybrid plugin still receives the corresponding Python setting delivery.</p>
     *
     * @param key declared setting key; never {@code null}
     * @param value new boxed setting value; never {@code null}
     * @throws Exception if the module cannot process the change
     */
    default void onSettingChanged(String key, Object value) throws Exception {
    }

    /**
     * Releases module-owned state during teardown.
     *
     * <p>The context is already inactive and its worker resources have been asked to close before
     * this callback. Host-managed UI resources are acknowledged on the main thread before module
     * teardown proceeds. The host may call this method again when a previous cleanup attempt
     * failed, so successful cleanup steps should be idempotent.</p>
     *
     * @throws Exception if cleanup is incomplete and must be retried
     */
    void onUnload() throws Exception;
}
