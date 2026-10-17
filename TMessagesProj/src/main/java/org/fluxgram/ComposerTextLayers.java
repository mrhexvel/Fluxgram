package org.fluxgram;

public final class ComposerTextLayers {
    public static final int MAX_LAYERS = 2;

    private final ComposerTextPresentation[] presentations;

    public ComposerTextLayers(ComposerTextPresentation[] presentations) {
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
