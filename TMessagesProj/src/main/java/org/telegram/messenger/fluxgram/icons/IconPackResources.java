package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public final class IconPackResources extends Resources {
    private final Resources resources;

    public IconPackResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());

        this.resources = resources;
    }

    @Override
    public Drawable getDrawableForDensity(int id, int density, Theme theme) throws NotFoundException {
        Drawable drawable = density == 0 || density == getDisplayMetrics().densityDpi
                ? IconPackRuntime.resolve(this, id)
                : null;

        if (drawable != null) {
            return drawable;
        }

        return resources.getDrawableForDensity(id, density, theme);
    }

    Drawable getOriginalDrawableForDensity(int id, int density, Theme theme)
            throws NotFoundException {
        return resources.getDrawableForDensity(id, density, theme);
    }
}
