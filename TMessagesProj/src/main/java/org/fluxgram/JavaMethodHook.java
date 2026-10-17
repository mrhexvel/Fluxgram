package org.fluxgram;

/**
 * Synchronous callbacks around one exact Java method or constructor.
 *
 * <p>Callbacks execute on the target invocation's actual thread and must not perform I/O, invoke
 * Python, wait for another thread, or retain the callback-scoped {@link JavaMethodHookCall}.
 * Failure quarantines this registration, rejects its changes, and preserves the current multi-hook
 * pipeline outcome. Three callbacks exceeding 4 ms also quarantine the registration.</p>
 */
public interface JavaMethodHook {
    /** Lowest accepted backend priority. Lower priorities enter later and exit earlier. */
    int PRIORITY_LOWEST = -10000;

    /** Default backend priority used by the two-argument registration overload. */
    int PRIORITY_DEFAULT = 50;

    /** Highest accepted backend priority. Higher priorities enter earlier and exit later. */
    int PRIORITY_HIGHEST = 10000;

    /**
     * Runs before the target member.
     *
     * <p>The callback may edit the fixed-size argument array, set an early result or throwable, or
     * invoke the original member exactly once through {@link JavaMethodHookCall#invokeOriginal()}.
     * Setting an outcome skips the normal invocation. Ordinary changes are committed only if the
     * owning generation is still running when the callback returns. Explicit original invocation
     * executes immediately; its side effects and outcome cannot be rolled back if the generation
     * becomes inactive before return.</p>
     *
     * @param call callback-scoped mutable call; valid only until this method returns
     * @throws Throwable to reject this callback's changes and quarantine the registration, except
     *                   that an uncaught throwable produced by explicit original invocation is
     *                   preserved as the original outcome without quarantine
     */
    default void before(JavaMethodHookCall call) throws Throwable {
    }

    /**
     * Runs after the target member or an earlier short-circuit, in reverse hook order.
     *
     * <p>The callback may replace the result or throwable. Explicit original invocation is not
     * available in this phase. Only registrations whose {@link #before(JavaMethodHookCall)} phase
     * was entered receive the matching after callback.</p>
     *
     * @param call callback-scoped mutable call containing the current result or throwable
     * @throws Throwable to preserve the current outcome and quarantine the registration
     */
    default void after(JavaMethodHookCall call) throws Throwable {
    }
}
