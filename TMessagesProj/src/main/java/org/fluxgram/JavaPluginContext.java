package org.fluxgram;

import java.lang.reflect.Member;

/**
 * Generation-scoped services supplied to a {@link JavaPluginModule}.
 *
 * <p>Except for identity queries, {@link #isActive()}, {@link #log(String)}, closing a
 * {@link JavaPluginUiResource}, and closing a {@link JavaMethodHookRegistration}, methods must be
 * called on the plugin worker thread while the generation is active. Calls rejected after
 * generation invalidation throw {@link IllegalStateException}. The context does not sandbox
 * plugin code.</p>
 */
public interface JavaPluginContext {
    /**
     * Returns the manifest plugin ID for this runtime.
     *
     * @return stable non-null plugin ID
     */
    String getPluginId();

    /**
     * Returns the runtime generation captured by this context.
     *
     * @return positive generation token
     */
    long getGeneration();

    /**
     * Reports whether this context still belongs to the current active generation.
     *
     * @return {@code true} only while new generation-scoped work may be accepted
     */
    boolean isActive();

    /**
     * Registers a worker-owned cleanup handle for this generation.
     *
     * <p>Managed handles close in reverse registration order. Cleanup continues after an
     * individual failure, and only failed handles are retried. Registering the same object more
     * than once does not create another owned entry.</p>
     *
     * @param resource non-null cleanup delegate
     * @return managed handle used to close this registration on the plugin worker thread
     * @throws IllegalArgumentException if {@code resource} is {@code null}
     * @throws IllegalStateException if the generation is inactive or its resource limit is reached
     */
    AutoCloseable registerResource(AutoCloseable resource);

    /**
     * Registers a managed hook with {@link JavaMethodHook#PRIORITY_DEFAULT}.
     *
     * @param member exact {@link java.lang.reflect.Method} or
     *               {@link java.lang.reflect.Constructor} to hook
     * @param hook non-null synchronous callback
     * @return lifecycle-managed registration
     */
    default JavaMethodHookRegistration registerMethodHook(Member member, JavaMethodHook hook) {
        return registerMethodHook(member, JavaMethodHook.PRIORITY_DEFAULT, hook);
    }

    /**
     * Registers a managed hook for an exact method or constructor.
     *
     * <p>Callbacks run synchronously on the target member's actual calling thread, not on the
     * plugin worker. The host checks runtime generation before entry and again before applying
     * changes. Callback failure rejects that callback's changes, preserves the current multi-hook
     * pipeline outcome, and quarantines this registration; three callbacks exceeding the 4 ms
     * diagnostic budget also quarantine it. Disable, unload, failed startup, removal, or explicit
     * close deactivates and unhooks the registration. In-flight callbacks are not interrupted and
     * cannot commit ordinary stale changes. An explicit original invocation executes immediately
     * and cannot be rolled back by generation invalidation.</p>
     *
     * @param member exact {@link java.lang.reflect.Method} or
     *               {@link java.lang.reflect.Constructor}; never {@code null}
     * @param priority value from {@link JavaMethodHook#PRIORITY_LOWEST} through
     *                 {@link JavaMethodHook#PRIORITY_HIGHEST}
     * @param hook non-null synchronous callback; it must not perform I/O, call Python, wait, or
     *             otherwise block the target thread
     * @return lifecycle-managed registration
     * @throws IllegalArgumentException if the member, priority, or callback is invalid
     * @throws IllegalStateException if this generation is inactive
     */
    default JavaMethodHookRegistration registerMethodHook(
            Member member, int priority, JavaMethodHook hook) {
        throw new UnsupportedOperationException("Method hooks require Java API 2.0");
    }

    /**
     * Registers a main-thread UI resource factory for this generation.
     *
     * <p>The factory is deferred until startup publishes the module as running. Creation and the
     * returned handle's cleanup both run on the Android main thread. A factory failure is reported
     * and closes only that handle; a {@code null} cleanup handle is treated as failure. Closing the
     * returned registration is non-blocking and may be requested from any thread.</p>
     *
     * @param factory non-null main-thread factory
     * @return generation-scoped asynchronous UI resource handle
     */
    default JavaPluginUiResource registerUiResource(JavaPluginUiResource.Factory factory) {
        throw new UnsupportedOperationException("Java UI resources require API 2.0");
    }

    /**
     * Registers a lifecycle-managed additive composer effect factory.
     *
     * @param factory non-null factory whose surface callbacks follow the composer API contract
     * @return generation-scoped UI resource handle
     */
    default JavaPluginUiResource registerComposerEffect(ComposerEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer effects require Java API 2.0");
    }

    /**
     * Registers the generation's lifecycle-managed native-text composer effect.
     *
     * @param factory non-null text-effect factory
     * @return generation-scoped UI resource handle
     */
    default JavaPluginUiResource registerComposerTextEffect(ComposerTextEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer text effects require Java API 2.0");
    }

    /**
     * Registers the generation's lifecycle-managed composer cursor effect.
     *
     * @param factory non-null cursor-effect factory
     * @return generation-scoped UI resource handle
     */
    default JavaPluginUiResource registerComposerCursorEffect(ComposerCursorEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer cursor effects require Java API 2.0");
    }

    /**
     * Registers the generation's lifecycle-managed additive selection effect.
     *
     * @param factory non-null selection-effect factory
     * @return generation-scoped UI resource handle
     */
    default JavaPluginUiResource registerComposerSelectionEffect(ComposerSelectionEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer selection effects require Java API 2.0");
    }

    /**
     * Registers the generation's lifecycle-managed composer content effect.
     *
     * @param factory non-null content-effect factory
     * @return generation-scoped UI resource handle
     */
    default JavaPluginUiResource registerComposerContentEffect(ComposerContentEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer content effects require Java API 2.0");
    }

    /**
     * Registers one candidate action for a single selected message in an ordinary cloud chat.
     *
     * <p>The host owns the toolbar item and chooses the first available registration in
     * deterministic order. Availability and selection callbacks run synchronously on the Android
     * main thread, are generation-gated, and must complete within the 4 ms callback budget. A
     * callback exception quarantines its binding; three budget overruns quarantine the
     * registration. Before invocation the host rechecks the account owner, screen lifecycle,
     * current message and availability. Failure or stale state simply leaves the action hidden or
     * unhandled.</p>
     *
     * @param id non-empty registration ID of at most 128 characters, containing only ASCII
     *           letters, digits, underscore, dot, or hyphen; unique for this extension
     * @param title non-empty accessible action title of at most 64 UTF-16 code units
     * @param icon supported semantic icon ID; use an empty string for no icon
     * @param action non-null action callback
     * @return generation-scoped UI resource handle
     * @throws IllegalArgumentException if metadata, icon, or callback is invalid
     */
    default JavaPluginUiResource registerChatSelectionAction(
            String id, String title, String icon, ChatSelectionAction action) {
        throw new UnsupportedOperationException("Chat selection actions require Java API 2.0");
    }

    /**
     * Reads a boolean setting, returning the fallback when no compatible value is stored.
     *
     * @param key setting key; never {@code null}
     * @param fallback value used when absent or stored with another type
     * @return stored value or {@code fallback}
     */
    boolean readBooleanSetting(String key, boolean fallback);

    /**
     * Reads an integer setting, returning the fallback when no compatible value is stored.
     *
     * @param key setting key; never {@code null}
     * @param fallback value used when absent or stored with another type
     * @return stored value or {@code fallback}
     */
    int readIntSetting(String key, int fallback);

    /**
     * Reads a string setting, returning the fallback when no compatible value is stored.
     *
     * @param key setting key; never {@code null}
     * @param fallback value used when absent or stored with another type; may be {@code null}
     * @return stored value or {@code fallback}, possibly {@code null}
     */
    String readStringSetting(String key, String fallback);

    /**
     * Writes a plugin-prefixed informational log entry.
     *
     * <p>This method may be called from any thread. Passing {@code null} logs the platform string
     * representation of {@code null}.</p>
     *
     * @param message message to log; may be {@code null}
     */
    void log(String message);
}
