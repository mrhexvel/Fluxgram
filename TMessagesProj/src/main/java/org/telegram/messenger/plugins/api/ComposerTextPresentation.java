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

    public ComposerTextPresentation(float translationX, float translationY, float alpha) {
        this(translationX, translationY, alpha, 1, 1, 0, 0, 0);
    }

    public ComposerTextPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        this.translationX = translationX;
        this.translationY = translationY;
        this.alpha = alpha;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.rotationDegrees = rotationDegrees;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
    }
}
