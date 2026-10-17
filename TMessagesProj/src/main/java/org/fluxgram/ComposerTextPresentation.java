package org.fluxgram;

/**
 * Immutable bounded presentation for host-captured pixels. Translation, pivot, and blur are in
 * physical pixels; alpha and tint amount are 0..1, scale is 0..4, and rotation is within +/-360.
 */
public class ComposerTextPresentation {
    /** Maximum accepted blur radius in capture-local physical pixels. */
    public static final float MAX_BLUR_RADIUS = 12;

    public final float translationX;
    public final float translationY;
    public final float alpha;
    public final float scaleX;
    public final float scaleY;
    public final float rotationDegrees;
    public final float pivotX;
    public final float pivotY;
    public final int tintColor;
    public final float tintAmount;
    public final float blurRadius;

    /** Creates a translated presentation with identity scale, rotation, tint, and blur. */
    public ComposerTextPresentation(float translationX, float translationY, float alpha) {
        this(translationX, translationY, alpha, 1, 1, 0, 0, 0, 0, 0, 0);
    }

    /** Creates a transformed presentation using a pivot inside the capture-local bounds. */
    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        this(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY, 0, 0, 0);
    }

    /** Creates a transformed presentation with opaque-RGB tint blending and no blur. */
    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount) {
        this(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY, tintColor, tintAmount, 0);
    }

    /**
     * Creates a complete presentation. The host rejects non-finite or out-of-range values and
     * transformed bounds outside its surface envelope.
     */
    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount, float blurRadius) {
        this.translationX = translationX;
        this.translationY = translationY;
        this.alpha = alpha;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.rotationDegrees = rotationDegrees;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.tintColor = tintColor;
        this.tintAmount = tintAmount;
        this.blurRadius = blurRadius;
    }
}
