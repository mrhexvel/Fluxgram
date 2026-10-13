package org.telegram.messenger.plugins.api;

public final class ComposerTextClusterPresentation {
    public final long revision;
    private final ComposerTextPresentation[] presentations;

    public ComposerTextClusterPresentation(long revision, ComposerTextPresentation[] presentations) {
        this.revision = revision;
        this.presentations = presentations == null
                ? new ComposerTextPresentation[0] : presentations.clone();
    }

    public int getCount() {
        return presentations.length;
    }

    public ComposerTextPresentation getPresentation(int index) {
        return presentations[index];
    }
}
