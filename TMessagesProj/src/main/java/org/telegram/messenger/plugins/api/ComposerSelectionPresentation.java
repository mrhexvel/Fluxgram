package org.telegram.messenger.plugins.api;

public final class ComposerSelectionPresentation extends ComposerTextPresentation {
    public ComposerSelectionPresentation(float translationX, float translationY, float alpha) {
        super(translationX, translationY, alpha);
    }

    public ComposerSelectionPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY);
    }

    public ComposerSelectionPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount, float blurRadius) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY,
                tintColor, tintAmount, blurRadius);
    }
}
