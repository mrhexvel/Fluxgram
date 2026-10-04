package org.telegram.messenger.plugins;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.plugins.packages.PluginPackageValidator;

import java.io.IOException;

public final class PluginIcons {
    public static Bitmap decode(byte[] data, String path) throws IOException {
        PluginPackageValidator.validateIcon(data, path);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > 1024 || bounds.outHeight > 1024) {
            throw new IOException("Invalid plugin icon dimensions");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;

        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 256) {
            options.inSampleSize *= 2;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

        if (bitmap == null) {
            throw new IOException("Cannot decode plugin icon");
        }

        return bitmap;
    }
}
