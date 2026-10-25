package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;

import com.caverock.androidsvg.SVG;

import org.telegram.messenger.R;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class IconPackAndroidCatalog implements IconPackArchiveValidator.Catalog {
    private static final int SVG_RENDER_SIZE = 256;
    private static final Set<String> HOST_MANAGED_NAMES = new HashSet<>(Arrays.asList(
            "boosts",
            "fab_compose_small",
            "instant_camera",
            "tabs_contact_active_24",
            "tabs_contacts_24",
            "tabs_chats_24",
            "tabs_chats_active_24",
            "tabs_calls_active_24",
            "tabs_calls_24"));

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
        return drawableId(resourceName) != 0 || HOST_MANAGED_NAMES.contains(resourceName);
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
        Bitmap bitmap = decodeSvg(bytes, path);

        if (bitmap == null) {
            return false;
        }

        bitmap.recycle();

        return true;
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
                    || bounds.outWidth > IconPackArchiveValidator.MAX_RASTER_DIMENSION
                    || bounds.outHeight > IconPackArchiveValidator.MAX_RASTER_DIMENSION
                    || (long) bounds.outWidth * bounds.outHeight
                            > IconPackArchiveValidator.MAX_RASTER_PIXELS) {
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            if (bitmap == null
                    || bitmap.getWidth() != bounds.outWidth
                    || bitmap.getHeight() != bounds.outHeight
                    || !hasVisiblePixels(bitmap)) {
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

    static Bitmap decodeSvg(byte[] bytes, String path) {
        if (bytes == null
                || bytes.length == 0
                || bytes.length > IconPackArchiveValidator.MAX_ICON_BYTES
                || path == null
                || !path.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            return null;
        }

        Bitmap bitmap = null;

        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            SVG svg = SVG.getFromString(decoded.toString());
            svg.setDocumentWidth(SVG_RENDER_SIZE);
            svg.setDocumentHeight(SVG_RENDER_SIZE);
            bitmap = Bitmap.createBitmap(
                    SVG_RENDER_SIZE,
                    SVG_RENDER_SIZE,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            svg.renderToCanvas(
                    canvas,
                    new RectF(0, 0, SVG_RENDER_SIZE, SVG_RENDER_SIZE));

            if (!hasVisiblePixels(bitmap)) {
                bitmap.recycle();

                return null;
            }

            return bitmap;
        } catch (Exception | OutOfMemoryError | StackOverflowError error) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }

            return null;
        }
    }

    static boolean hasVisiblePixels(Bitmap bitmap) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] row = new int[width];

            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);

                for (int pixel : row) {
                    if ((pixel >>> 24) != 0) {
                        return true;
                    }
                }
            }
        } catch (RuntimeException | OutOfMemoryError error) {
            return false;
        }

        return false;
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
