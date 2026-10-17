package org.fluxgram;

/** Bounded transform, tint, and blur applied to a host-captured native cursor. */
public final class ComposerCursorPresentation extends ComposerTextPresentation {
    /** Creates a translated presentation in pixels with alpha in the range 0..1. */
    public ComposerCursorPresentation(float translationX, float translationY, float alpha) {
        super(translationX, translationY, alpha);
    }

    /** Creates a transformed presentation using capture-local pixel pivots. */
    public ComposerCursorPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY);
    }

    /** Creates a transformed presentation with opaque-RGB tint blending and pixel blur radius. */
    public ComposerCursorPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount, float blurRadius) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY,
                tintColor, tintAmount, blurRadius);
    }
}
