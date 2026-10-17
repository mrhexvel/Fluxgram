package org.fluxgram;

public class ComposerTextPresentation {
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

    public ComposerTextPresentation(float translationX, float translationY, float alpha) {
        this(translationX, translationY, alpha, 1, 1, 0, 0, 0, 0, 0, 0);
    }

    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        this(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY, 0, 0, 0);
    }

    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount) {
        this(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY, tintColor, tintAmount, 0);
    }

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
