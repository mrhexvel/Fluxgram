package org.telegram.messenger.plugins.api;

public final class ComposerTextPresentation {
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

    public ComposerTextPresentation(float translationX, float translationY, float alpha) {
        this(translationX, translationY, alpha, 1, 1, 0, 0, 0, 0, 0);
    }

    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        this(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY, 0, 0);
    }

    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount) {
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
    }
}
