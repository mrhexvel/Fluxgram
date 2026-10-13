package org.telegram.messenger.plugins.api;

public final class ComposerTextClusterPresentation {
    public final long revision;
    private final ComposerTextLayers[] layers;

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

    public static ComposerTextClusterPresentation layered(
            long revision, ComposerTextLayers[] layers) {
        return new ComposerTextClusterPresentation(revision, layers);
    }

    public int getCount() {
        return layers.length;
    }

    public ComposerTextPresentation getPresentation(int index) {
        ComposerTextLayers item = layers[index];
        return item == null || item.getCount() == 0 ? null : item.getPresentation(0);
    }

    public ComposerTextLayers getLayers(int index) {
        return layers[index];
    }
}
