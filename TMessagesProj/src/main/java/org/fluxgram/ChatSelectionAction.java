package org.fluxgram;

/**
 * Plugin policy for one host-owned action in the single-message chat selection toolbar.
 *
 * <p>Both callbacks run synchronously on the Android main thread. They must not perform I/O, call
 * Python, wait, or otherwise block. The host generation-gates each invocation, attributes callback
 * failures, and applies a 4 ms budget; a failing binding or a registration with three overruns is
 * quarantined. A callback cannot mutate Telegram message state through the snapshot.</p>
 */
public interface ChatSelectionAction {
    /**
     * Decides whether the action is available for the current selected message.
     *
     * <p>The host invokes this while building the toolbar and invokes it again immediately before
     * {@link #onSelected(ChatSelectionMessageSnapshot)}. Returning {@code false}, throwing, losing
     * the generation, or exceeding the quarantine threshold causes this registration to contribute
     * no action; the next eligible registration may be considered.</p>
     *
     * @param message immutable, non-null snapshot of the selected message identity
     * @return {@code true} to offer this action for this message
     * @throws Exception if availability evaluation fails
     */
    default boolean isAvailable(ChatSelectionMessageSnapshot message) throws Exception {
        return true;
    }

    /**
     * Handles selection of the host-owned toolbar action.
     *
     * <p>Before this call the host revalidates the active account owner, screen lifecycle, current
     * message and availability. An exception quarantines this binding and leaves the click
     * unhandled; there is no retry or automatic mutation.</p>
     *
     * @param message immutable, non-null snapshot re-created from the current selected message
     * @throws Exception if the action cannot be completed
     */
    void onSelected(ChatSelectionMessageSnapshot message) throws Exception;
}
