package org.fluxgram;

/** Immutable ordered selection of exact shaping-cluster ranges for one layout revision. */
public final class ComposerTextClusterSelection {
    /** Maximum candidate count accepted by the host. */
    public static final int MAX_CLUSTERS = 64;

    public final long revision;
    private final ComposerTextCandidate[] candidates;

    /** Creates a selection and defensively copies its candidate array. */
    public ComposerTextClusterSelection(long revision, ComposerTextCandidate[] candidates) {
        this.revision = revision;
        this.candidates = candidates == null ? new ComposerTextCandidate[0] : candidates.clone();
    }

    /** Returns the number of selected cluster ranges. */
    public int getCount() {
        return candidates.length;
    }

    /** Returns a selected exact cluster range by index. */
    public ComposerTextCandidate getCandidate(int index) {
        return candidates[index];
    }
}
