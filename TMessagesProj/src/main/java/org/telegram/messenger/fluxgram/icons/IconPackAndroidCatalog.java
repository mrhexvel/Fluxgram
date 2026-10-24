package org.telegram.messenger.fluxgram.icons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class IconPackAndroidCatalog implements IconPackArchiveValidator.Catalog {
    private static final Map<String, Integer> DRAWABLES;

    static {
        LinkedHashMap<String, Integer> drawables = new LinkedHashMap<>();

        drawables.put("msg_add", R.drawable.msg_add);
        drawables.put("msg_copy", R.drawable.msg_copy);
        drawables.put("msg_delete", R.drawable.msg_delete);
        drawables.put("msg_download", R.drawable.msg_download);
        drawables.put("msg_edit", R.drawable.msg_edit);
        drawables.put("msg_forward", R.drawable.msg_forward);
        drawables.put("msg_gallery", R.drawable.msg_gallery);
        drawables.put("msg_search", R.drawable.msg_search);
        drawables.put("msg_select", R.drawable.msg_select);
        drawables.put("msg_send", R.drawable.msg_send);
        drawables.put("msg_settings", R.drawable.msg_settings);
        drawables.put("msg_share", R.drawable.msg_share);

        DRAWABLES = Collections.unmodifiableMap(drawables);
    }

    @Override
    public boolean contains(String resourceName) {
        return DRAWABLES.containsKey(resourceName);
    }

    public int drawableId(String resourceName) {
        Integer drawable = DRAWABLES.get(resourceName);

        return drawable == null ? 0 : drawable;
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

        return normalized.endsWith(".png") || normalized.endsWith(".webp");
    }
}
