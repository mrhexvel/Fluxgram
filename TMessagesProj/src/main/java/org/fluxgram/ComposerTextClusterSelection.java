package org.fluxgram;

public final class ComposerTextClusterSelection {
    public static final int MAX_CLUSTERS = 64;

    public final long revision;
    private final ComposerTextCandidate[] candidates;

    public ComposerTextClusterSelection(long revision, ComposerTextCandidate[] candidates) {
        this.revision = revision;
        this.candidates = candidates == null ? new ComposerTextCandidate[0] : candidates.clone();
    }

    public int getCount() {
        return candidates.length;
    }

    public ComposerTextCandidate getCandidate(int index) {
        return candidates[index];
    }
}
