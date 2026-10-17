package org.fluxgram;

/** Immutable revision-bound presentation list for an exact cluster-capture batch. */
public final class ComposerTextClusterPresentation {
    public final long revision;
    private final ComposerTextLayers[] layers;

    /**
     * Creates a compatibility presentation with zero or one layer per capture. The array length
     * must match the capture count and is defensively copied into layer entries.
     */
    public ComposerTextClusterPresentation(long revision, ComposerTextPresentation[] presentations) {
        this.revision = revision;
        if (presentations == null) {
            layers = new ComposerTextLayers[0];
            return;
        }

        layers = new ComposerTextLayers[presentations.length];

        for (int index = 0; index < presentations.length; index++) {
            if (presentations[index] != null) {
                layers[index] = new ComposerTextLayers(
                        new ComposerTextPresentation[] {presentations[index]});
            }
        }
    }

    private ComposerTextClusterPresentation(long revision, ComposerTextLayers[] layers) {
        this.revision = revision;
        this.layers = layers == null ? new ComposerTextLayers[0] : layers.clone();
    }

    /**
     * Creates a presentation with up to two ordered layers per capture. The array length must
     * match the capture count; the outer array is defensively copied.
     */
    public static ComposerTextClusterPresentation layered(
            long revision, ComposerTextLayers[] layers) {
        return new ComposerTextClusterPresentation(revision, layers);
    }

    /** Returns the number of per-capture presentation entries. */
    public int getCount() {
        return layers.length;
    }

    /** Returns the first compatibility presentation for an entry, or {@code null}. */
    public ComposerTextPresentation getPresentation(int index) {
        ComposerTextLayers item = layers[index];
        return item == null || item.getCount() == 0 ? null : item.getPresentation(0);
    }

    /** Returns all ordered layers for an entry, or {@code null} for native drawing of that item. */
    public ComposerTextLayers getLayers(int index) {
        return layers[index];
    }
}
