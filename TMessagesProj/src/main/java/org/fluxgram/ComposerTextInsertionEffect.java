package org.fluxgram;

/** Observes insertions whose complete UTF-16 range has exact bounded shaping-cluster geometry. */
public interface ComposerTextInsertionEffect extends ComposerEffect {
    /** Receives an immutable insertion on the main thread after final layout for the frame. */
    void onTextInserted(ComposerTextInsertion insertion) throws Exception;
}
