package org.fluxgram;

/** Immutable ordered presentations that replay one host capture at most twice. */
public final class ComposerTextLayers {
    /** Maximum accepted layer count. */
    public static final int MAX_LAYERS = 2;

    private final ComposerTextPresentation[] presentations;

    /** Creates a layer list and defensively copies the supplied array. */
    public ComposerTextLayers(ComposerTextPresentation[] presentations) {
        this.presentations = presentations == null
                ? new ComposerTextPresentation[0] : presentations.clone();
    }

    /** Returns the number of layers; the host accepts one or two. */
    public int getCount() {
        return presentations.length;
    }

    /** Returns a presentation in draw order. */
    public ComposerTextPresentation getPresentation(int index) {
        return presentations[index];
    }
}
