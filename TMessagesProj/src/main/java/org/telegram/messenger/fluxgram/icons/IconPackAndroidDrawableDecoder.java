package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.util.Locale;

public final class IconPackAndroidDrawableDecoder {
    private IconPackAndroidDrawableDecoder() {}

    public static Drawable resolve(
            Resources resources,
            IconPackStore store,
            IconPackStore.Installed installed,
            String resourceName) {
        if (resources == null
                || store == null
                || installed == null
                || !new IconPackAndroidCatalog(resources).contains(resourceName)) {
            return null;
        }

        String path = installed.manifest.icons.get(resourceName);

        if (path == null) {
            return null;
        }

        try {
            return decodeVerified(resources, store.resolve(installed, resourceName), path);
        } catch (IOException error) {
            return null;
        }
    }

    static Drawable decodeVerified(Resources resources, byte[] bytes, String path) {
        if (resources == null
                || bytes == null
                || bytes.length == 0
                || bytes.length > IconPackArchiveValidator.MAX_ICON_BYTES
                || path == null) {
            return null;
        }

        String normalized = path.toLowerCase(Locale.ROOT);

        if (normalized.endsWith(".svg")) {
            Bitmap bitmap = IconPackAndroidCatalog.decodeSvg(bytes, path);

            if (bitmap == null) {
                return null;
            }

            try {
                return new BitmapDrawable(resources, bitmap);
            } catch (RuntimeException | OutOfMemoryError error) {
                bitmap.recycle();

                return null;
            }
        }
        if (!normalized.endsWith(".png")
                && !normalized.endsWith(".webp")
                && !normalized.endsWith(".jpg")
                && !normalized.endsWith(".jpeg")) {
            return null;
        }

        Bitmap bitmap = IconPackAndroidCatalog.decodeRaster(bytes, path, 0, 0);

        if (bitmap == null) {
            return null;
        }

        try {
            return new BitmapDrawable(resources, bitmap);
        } catch (RuntimeException | OutOfMemoryError error) {
            bitmap.recycle();

            return null;
        }
    }
}
