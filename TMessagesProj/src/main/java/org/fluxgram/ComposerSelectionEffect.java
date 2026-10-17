package org.fluxgram;

/** Exclusive owner of an additive decoration over the unchanged native selection highlight. */
public interface ComposerSelectionEffect extends ComposerEffect {
    /** Creates one selection effect per eligible surface on the main thread. */
    interface Factory {
        /** Creates a lifecycle-owned selection effect for {@code surface}. */
        ComposerSelectionEffect create(ComposerSurface surface) throws Exception;
    }

    /**
     * Returns an additive presentation on the main thread, or {@code null} to draw no decoration.
     * Stale captures are discarded; invalid values suspend the registration.
     */
    ComposerSelectionPresentation presentSelection(ComposerSelectionSnapshot selection) throws Exception;
}
