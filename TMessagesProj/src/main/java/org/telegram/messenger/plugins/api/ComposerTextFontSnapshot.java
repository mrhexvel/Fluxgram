package org.telegram.messenger.plugins.api;

public final class ComposerTextFontSnapshot {
    public final float textSize;
    public final float scaleX;
    public final float skewX;
    public final float letterSpacing;
    public final int flags;
    public final int color;
    public final int typefaceStyle;
    public final int typefaceWeight;
    public final String locales;
    public final String featureSettings;
    public final String variationSettings;

    public ComposerTextFontSnapshot(float textSize, float scaleX, float skewX, float letterSpacing,
            int flags, int color, int typefaceStyle, int typefaceWeight,
            String locales, String featureSettings, String variationSettings) {
        this.textSize = textSize;
        this.scaleX = scaleX;
        this.skewX = skewX;
        this.letterSpacing = letterSpacing;
        this.flags = flags;
        this.color = color;
        this.typefaceStyle = typefaceStyle;
        this.typefaceWeight = typefaceWeight;
        this.locales = locales;
        this.featureSettings = featureSettings;
        this.variationSettings = variationSettings;
    }
}
