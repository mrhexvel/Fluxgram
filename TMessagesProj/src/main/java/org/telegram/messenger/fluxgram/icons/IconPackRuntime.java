package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IconPackRuntime {
    private static final int ICON_SIZE_DP = 24;
    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile long revision;

    private IconPackRuntime() {
    }

    public static Drawable resolve(Resources resources, int drawableId) {
        if (resources == null || drawableId == 0) {
            return null;
        }

        Snapshot current = snapshot;

        if (current.densityDpi != resources.getDisplayMetrics().densityDpi) {
            return null;
        }

        Bitmap bitmap = current.bitmaps.get(drawableId);

        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }

        try {
            return new BitmapDrawable(resources, bitmap);
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    public static void setImageResource(ImageView imageView, int drawableId) {
        Drawable drawable = resolve(imageView.getResources(), drawableId);

        if (drawable != null) {
            imageView.setImageDrawable(drawable);
        } else {
            imageView.setImageResource(drawableId);
        }
    }

    public static long revision() {
        return revision;
    }

    static boolean rebuild(
            Resources resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds) {
        if (resources == null || store == null || installed == null || activeIds == null) {
            return publish(Snapshot.empty());
        }

        HashMap<String, IconPackStore.Installed> byId = new HashMap<>();

        for (IconPackStore.Installed pack : installed) {
            byId.put(pack.manifest.id, pack);
        }

        int densityDpi = resources.getDisplayMetrics().densityDpi;
        int size = Math.max(1, Math.round(ICON_SIZE_DP * densityDpi
                / (float) DisplayMetrics.DENSITY_DEFAULT));
        IconPackAndroidCatalog catalog = new IconPackAndroidCatalog(resources);
        HashMap<Integer, Bitmap> bitmaps = new HashMap<>();
        HashMap<Integer, String> sources = new HashMap<>();

        for (String id : activeIds) {
            IconPackStore.Installed pack = byId.get(id);

            if (pack == null) {
                continue;
            }

            for (Map.Entry<String, String> entry : pack.manifest.icons.entrySet()) {
                String resourceName = entry.getKey();
                int drawableId = catalog.drawableId(resourceName);

                if (drawableId == 0 || bitmaps.containsKey(drawableId)) {
                    continue;
                }

                Drawable drawable = IconPackAndroidDrawableDecoder.resolve(
                        resources,
                        store,
                        pack,
                        resourceName);
                Bitmap bitmap = render(drawable, size, densityDpi);

                if (bitmap != null) {
                    bitmaps.put(drawableId, bitmap);
                    sources.put(
                            drawableId,
                            pack.manifest.id + "\n" + pack.generation() + "\n"
                                    + entry.getValue());
                }
            }
        }

        if (bitmaps.isEmpty()) {
            return publish(Snapshot.empty());
        }

        return publish(new Snapshot(densityDpi, bitmaps, sources));
    }

    static boolean clear() {
        return publish(Snapshot.empty());
    }

    private static Bitmap render(Drawable drawable, int size, int densityDpi) {
        if (drawable == null) {
            return null;
        }

        Bitmap result = null;

        try {
            result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            result.setDensity(densityDpi);
            Canvas canvas = new Canvas(result);
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();

            if (width <= 0 || height <= 0) {
                width = height = size;
            }

            float scale = Math.min(size / (float) width, size / (float) height);
            int targetWidth = Math.max(1, Math.round(width * scale));
            int targetHeight = Math.max(1, Math.round(height * scale));
            int left = (size - targetWidth) / 2;
            int top = (size - targetHeight) / 2;
            drawable.setBounds(left, top, left + targetWidth, top + targetHeight);
            drawable.draw(canvas);

            return result;
        } catch (RuntimeException | OutOfMemoryError error) {
            if (result != null) {
                result.recycle();
            }

            return null;
        } finally {
            if (drawable instanceof BitmapDrawable) {
                Bitmap source = ((BitmapDrawable) drawable).getBitmap();

                if (source != null && source != result && !source.isRecycled()) {
                    source.recycle();
                }
            }
        }
    }

    private static synchronized boolean publish(Snapshot next) {
        Snapshot current = snapshot;

        if (current.densityDpi == next.densityDpi && current.sources.equals(next.sources)) {
            recycle(next.bitmaps);

            return false;
        }

        snapshot = next;
        revision++;

        return true;
    }

    private static void recycle(Map<Integer, Bitmap> bitmaps) {
        for (Bitmap bitmap : bitmaps.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private static final class Snapshot {
        private final int densityDpi;
        private final Map<Integer, Bitmap> bitmaps;
        private final Map<Integer, String> sources;

        private Snapshot(
                int densityDpi,
                Map<Integer, Bitmap> bitmaps,
                Map<Integer, String> sources) {
            this.densityDpi = densityDpi;
            this.bitmaps = Collections.unmodifiableMap(new HashMap<>(bitmaps));
            this.sources = Collections.unmodifiableMap(new HashMap<>(sources));
        }

        private static Snapshot empty() {
            return new Snapshot(0, Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
