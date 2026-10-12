package org.telegram.messenger.plugins.api;

public final class ComposerTextPresentation {
    public final float translationX;
    public final float translationY;
    public final float alpha;

    public ComposerTextPresentation(float translationX, float translationY, float alpha) {
        this.translationX = translationX;
        this.translationY = translationY;
        this.alpha = alpha;
    }
}
