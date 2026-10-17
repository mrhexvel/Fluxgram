package org.fluxgram;

import java.lang.reflect.Member;

/**
 * Lifecycle-managed registration for one exact hooked member.
 *
 * <p>The registration becomes inactive on explicit close, generation invalidation, callback
 * failure, or callback-budget quarantine. Host teardown removes the backend hook automatically.
 * In-flight callbacks are allowed to finish, but ordinary stale changes are not committed and
 * plugin references are released after the last callback exits. An explicit original invocation
 * already performed by the callback cannot be rolled back during invalidation.</p>
 */
public interface JavaMethodHookRegistration extends AutoCloseable {
    /**
     * Returns the exact method or constructor supplied at registration.
     *
     * @return non-null hooked member
     */
    Member getMember();

    /**
     * Reports whether this registration and its owning generation can currently dispatch.
     *
     * @return {@code true} only while both registration and generation are active
     */
    boolean isActive();

    /**
     * Deactivates this registration and requests backend unhook.
     *
     * <p>The call is idempotent after successful cleanup. It may be made from any thread; a call
     * outside the plugin worker deactivates dispatch immediately and schedules owned-resource
     * cleanup on the worker. Failed backend cleanup remains owned by the host for retry.</p>
     *
     * @throws Exception if synchronous cleanup cannot complete
     */
    @Override
    void close() throws Exception;
}
