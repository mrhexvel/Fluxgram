package org.telegram.messenger.plugins.api;

public final class ComposerGeometry {
    public final int width;
    public final int height;
    public final float density;
    public final int accentColor;
    public final boolean rtl;

    public ComposerGeometry(int width, int height, float density, int accentColor, boolean rtl) {
        this.width = width;
        this.height = height;
        this.density = density;
        this.accentColor = accentColor;
        this.rtl = rtl;
    }
}
