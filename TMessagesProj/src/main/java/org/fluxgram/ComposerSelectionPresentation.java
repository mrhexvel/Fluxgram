package org.fluxgram;

/** Bounded transform, tint, and blur for an additive host-captured selection mask. */
public final class ComposerSelectionPresentation extends ComposerTextPresentation {
    /** Creates a translated presentation in pixels with alpha in the range 0..1. */
    public ComposerSelectionPresentation(float translationX, float translationY, float alpha) {
        super(translationX, translationY, alpha);
    }

    /** Creates a transformed presentation using capture-local pixel pivots. */
    public ComposerSelectionPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY);
    }

    /** Creates a transformed presentation with opaque-RGB tint blending and pixel blur radius. */
    public ComposerSelectionPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount, float blurRadius) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY,
                tintColor, tintAmount, blurRadius);
    }
}
