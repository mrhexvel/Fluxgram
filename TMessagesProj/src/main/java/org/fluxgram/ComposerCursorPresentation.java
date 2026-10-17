package org.fluxgram;

public final class ComposerCursorPresentation extends ComposerTextPresentation {
    public ComposerCursorPresentation(float translationX, float translationY, float alpha) {
        super(translationX, translationY, alpha);
    }

    public ComposerCursorPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY);
    }

    public ComposerCursorPresentation(float translationX, float translationY, float alpha,
            float scaleX, float scaleY, float rotationDegrees, float pivotX, float pivotY,
            int tintColor, float tintAmount, float blurRadius) {
        super(translationX, translationY, alpha,
                scaleX, scaleY, rotationDegrees, pivotX, pivotY,
                tintColor, tintAmount, blurRadius);
    }
}
