package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class IconPackAndroidCatalog implements IconPackArchiveValidator.Catalog {
    private final Resources resources;
    private final String packageName;

    public IconPackAndroidCatalog(Resources resources) {
        if (resources == null) {
            throw new IllegalArgumentException("Resources are required");
        }

        this.resources = resources;
        this.packageName = resources.getResourcePackageName(R.drawable.msg_add);
    }

    @Override
    public boolean contains(String resourceName) {
        return drawableId(resourceName) != 0;
    }

    public int drawableId(String resourceName) {
        if (!IconPackManifest.isValidResourceName(resourceName)) {
            return 0;
        }

        return resources.getIdentifier(resourceName, "drawable", packageName);
    }

    public String resourceName(int drawableId) {
        if (drawableId == 0) {
            return null;
        }

        try {
            if (!"drawable".equals(resources.getResourceTypeName(drawableId))
                    || !packageName.equals(resources.getResourcePackageName(drawableId))) {
                return null;
            }

            return resources.getResourceEntryName(drawableId);
        } catch (Resources.NotFoundException error) {
            return null;
        }
    }

    @Override
    public boolean decodesRaster(byte[] bytes, String path, int width, int height) {
        Bitmap bitmap = decodeRaster(bytes, path, width, height);

        if (bitmap == null) {
            return false;
        }

        bitmap.recycle();

        return true;
    }

    @Override
    public boolean decodesSvg(byte[] bytes, String path) {
        return decodeSvg(bytes, path) != null;
    }

    static Bitmap decodeRaster(byte[] bytes, String path, int expectedWidth, int expectedHeight) {
        try {
            if (bytes == null
                    || bytes.length == 0
                    || bytes.length > IconPackArchiveValidator.MAX_ICON_BYTES
                    || !isRasterPath(path)
                    || expectedWidth < 0
                    || expectedHeight < 0
                    || (expectedWidth == 0) != (expectedHeight == 0)) {
                return null;
            }

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

            if ((expectedWidth > 0
                            && (bounds.outWidth != expectedWidth
                                    || bounds.outHeight != expectedHeight))
                    || bounds.outWidth <= 0
                    || bounds.outHeight <= 0
                    || bounds.outWidth > 2048
                    || bounds.outHeight > 2048) {
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            if (bitmap == null
                    || bitmap.getWidth() != bounds.outWidth
                    || bitmap.getHeight() != bounds.outHeight) {
                if (bitmap != null) {
                    bitmap.recycle();
                }

                return null;
            }

            return bitmap;
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    static SvgHelper.SvgDrawable decodeSvg(byte[] bytes, String path) {
        if (bytes == null
                || bytes.length == 0
                || bytes.length > IconPackArchiveValidator.MAX_ICON_BYTES
                || path == null
                || !path.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return null;
        }

        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));

            return SvgHelper.getDrawable(decoded.toString());
        } catch (CharacterCodingException | RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    private static boolean isRasterPath(String path) {
        if (path == null) {
            return false;
        }

        String normalized = path.toLowerCase(Locale.ROOT);

        return normalized.endsWith(".png")
                || normalized.endsWith(".webp")
                || normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg");
    }
}
