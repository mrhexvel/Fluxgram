package org.telegram.messenger.plugins.api;

public final class ComposerTextCandidate {
    public final long revision;
    public final int start;
    public final int end;

    public ComposerTextCandidate(long revision, int start, int end) {
        this.revision = revision;
        this.start = start;
        this.end = end;
    }
}
