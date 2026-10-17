package org.fluxgram;

/** Immutable field geometry and theme data used by composer draw callbacks. */
public final class ComposerGeometry {
    public final int width;
    public final int height;
    public final float density;
    public final int accentColor;
    public final boolean rtl;

    /**
     * Creates a geometry snapshot. Width and height are physical pixels, density converts dp to
     * pixels, and {@code accentColor} is an ARGB theme color.
     */
    public ComposerGeometry(int width, int height, float density, int accentColor, boolean rtl) {
        this.width = width;
        this.height = height;
        this.density = density;
        this.accentColor = accentColor;
        this.rtl = rtl;
    }
}
