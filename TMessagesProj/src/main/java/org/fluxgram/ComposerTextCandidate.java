package org.fluxgram;

/** A requested non-empty UTF-16 text range tied to an exact composer revision. */
public final class ComposerTextCandidate {
    public final long revision;
    public final int start;
    public final int end;

    /** Creates a half-open UTF-16 range {@code [start, end)} for {@code revision}. */
    public ComposerTextCandidate(long revision, int start, int end) {
        this.revision = revision;
        this.start = start;
        this.end = end;
    }
}
