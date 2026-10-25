package android.graphics;

import java.nio.charset.StandardCharsets;

public final class BitmapFactory {
    public static final class Options {
        public boolean inJustDecodeBounds;
        public int outWidth;
        public int outHeight;
    }

    public static Bitmap decodeByteArray(byte[] bytes, int offset, int length) {
        return decodeByteArray(bytes, offset, length, null);
    }

    public static Bitmap decodeByteArray(byte[] bytes, int offset, int length, Options options) {
        String value = new String(bytes, offset, length, StandardCharsets.UTF_8);

        if (options != null && options.inJustDecodeBounds) {
            if (value.startsWith("bounds-runtime:")) {
                throw new IllegalStateException();
            }
            if (value.startsWith("bounds-oom:")) {
                throw new OutOfMemoryError();
            }
        } else {
            if (value.startsWith("decode-runtime:")) {
                throw new IllegalStateException();
            }
            if (value.startsWith("decode-oom:")) {
                throw new OutOfMemoryError();
            }
        }

        String[] parts = value.split(":");

        if (parts.length != 3) {
            if (options != null) {
                options.outWidth = -1;
                options.outHeight = -1;
            }

            return null;
        }

        int width = Integer.parseInt(parts[1]);
        int height = Integer.parseInt(parts[2]);

        if (options != null) {
            options.outWidth = width;
            options.outHeight = height;

            if (options.inJustDecodeBounds) {
                return null;
            }
        }
        if (parts[0].equals("decode-null")) {
            return null;
        }
        if (parts[0].equals("decoded-mismatch")) {
            return new Bitmap(width + 1, height);
        }

        Bitmap bitmap = parts[0].equals("raster")
                        || parts[0].equals("transparent")
                        || parts[0].equals("bounds-runtime")
                        || parts[0].equals("bounds-oom")
                        || parts[0].equals("decode-runtime")
                        || parts[0].equals("decode-oom")
                ? new Bitmap(width, height)
                : null;

        if (bitmap != null && parts[0].equals("transparent")) {
            bitmap.markTransparent();
        }

        return bitmap;
    }

    private BitmapFactory() {}
}
