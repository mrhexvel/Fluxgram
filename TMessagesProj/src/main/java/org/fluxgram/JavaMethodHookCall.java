package org.fluxgram;

import java.lang.reflect.Member;

/**
 * Mutable, callback-scoped view of one hooked invocation.
 *
 * <p>Every method throws {@link IllegalStateException} after the corresponding hook callback
 * returns. Do not retain this object or its argument array. Argument and result types are validated
 * before they are committed to the backend.</p>
 */
public interface JavaMethodHookCall {
    /** Identifies which side of the target invocation is currently executing. */
    enum Phase {
        /** Callback is running before the target member. */
        BEFORE,

        /** Callback is running after the current target outcome is available. */
        AFTER
    }

    /**
     * Returns the current callback phase.
     *
     * @return {@link Phase#BEFORE} or {@link Phase#AFTER}
     */
    Phase getPhase();

    /**
     * Returns the exact hooked method or constructor.
     *
     * @return non-null member supplied at registration
     */
    Member getMember();

    /**
     * Returns the receiver object.
     *
     * @return receiver for an instance invocation when available; {@code null} for a static method
     *         and possibly before constructor initialization
     */
    Object getThisObject();

    /**
     * Returns the mutable, fixed-length argument array for this callback.
     *
     * <p>Element changes made in before are passed to the normal or explicitly invoked original
     * member after type validation. The array itself is a host-owned copy and must not be retained.
     * Its length must not be changed.</p>
     *
     * @return non-null argument array, empty for a member with no parameters
     */
    Object[] getArguments();

    /**
     * Returns the current result.
     *
     * @return current result, or {@code null} before execution, for a void/constructor result, for
     *         a legitimate null result, or while a throwable is set
     */
    Object getResult();

    /**
     * Returns the current throwable outcome.
     *
     * @return current throwable, or {@code null} when the current outcome is a result
     */
    Throwable getThrowable();

    /**
     * Reports whether the current outcome is a throwable.
     *
     * @return {@code true} when {@link #getThrowable()} is non-null
     */
    boolean hasThrowable();

    /**
     * Replaces the current outcome with a result and clears any throwable.
     *
     * <p>In the before phase this short-circuits normal target invocation. The value must match the
     * method return type; constructors and void methods accept only {@code null}.</p>
     *
     * @param result replacement result, possibly {@code null} for a compatible return type
     * @throws IllegalArgumentException if the value is incompatible with the member
     */
    void setResult(Object result);

    /**
     * Replaces the current outcome with a throwable and clears the result.
     *
     * <p>In the before phase this short-circuits normal target invocation.</p>
     *
     * @param throwable non-null replacement throwable
     * @throws IllegalArgumentException if {@code throwable} is {@code null}
     */
    void setThrowable(Throwable throwable);

    /**
     * Immediately invokes the original member once with the current receiver and argument values.
     *
     * <p>This is available only during before. If no later result or throwable is set, the host
     * publishes this explicit invocation's outcome and prevents a second normal invocation. If
     * plugin code fails after the explicit call, fallback still reuses that outcome rather than
     * running the original twice.</p>
     *
     * @return original result, including {@code null} for constructors, void methods, or a null
     *         method result
     * @throws Throwable the original member's throwable
     * @throws IllegalStateException in after or after the original has already been invoked
     * @throws IllegalArgumentException if a modified argument is incompatible
     */
    Object invokeOriginal() throws Throwable;
}
