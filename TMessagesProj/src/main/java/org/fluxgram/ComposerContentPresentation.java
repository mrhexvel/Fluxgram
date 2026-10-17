package org.fluxgram;

/** Immutable bounded translation of the complete native composer content. */
public final class ComposerContentPresentation {
    /** Maximum absolute translation on either axis, in dp. */
    public static final float MAX_TRANSLATION_DP = 24;

    public final float translationX;
    public final float translationY;

    /** Creates a translation in physical pixels; each axis must remain within 24 dp. */
    public ComposerContentPresentation(float translationX, float translationY) {
        this.translationX = translationX;
        this.translationY = translationY;
    }
}
