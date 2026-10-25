package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

public final class IconPackRuntime {
    private static final int FALLBACK_ICON_SIZE_DP = 24;
    private static final int MAX_CACHE_BYTES = 32 * 1024 * 1024;
    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile long revision;
    private static final LinkedHashMap<String, Bitmap> bitmapCache =
            new LinkedHashMap<>(16, 0.75f, true);
    private static final WeakHashMap<ImageView, ImageBinding> imageBindings = new WeakHashMap<>();
    private static final WeakHashMap<Listener, Boolean> listeners = new WeakHashMap<>();
    private static int bitmapCacheBytes;

    public interface Listener {
        void onIconPackRuntimeChanged();
    }

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

    public static Drawable resolve(Resources resources, String resourceName) {
        if (resources == null || !IconPackManifest.isValidResourceName(resourceName)) {
            return null;
        }

        Snapshot current = snapshot;

        if (current.densityDpi != resources.getDisplayMetrics().densityDpi) {
            return null;
        }

        Bitmap bitmap = current.namedBitmaps.get(resourceName);

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
        applyImageResource(imageView, drawableId);
        trackImageResource(imageView, drawableId);
    }

    public static void trackImageResource(ImageView imageView, int drawableId) {
        if (imageView == null || drawableId == 0) {
            unbindImageView(imageView);

            return;
        }

        synchronized (imageBindings) {
            imageBindings.put(
                    imageView,
                    new ImageBinding(drawableId, imageView.getDrawable(), null));
        }
    }

    public static void trackAnimatedImageResource(
            ImageView imageView,
            int drawableId,
            Drawable animationTarget) {
        if (imageView == null || drawableId == 0 || animationTarget == null) {
            unbindImageView(imageView);

            return;
        }

        ImageBinding binding = new ImageBinding(
                drawableId,
                imageView.getDrawable(),
                animationTarget);

        synchronized (imageBindings) {
            imageBindings.put(imageView, binding);
        }

        imageView.postOnAnimation(() -> finishAnimatedBinding(imageView, binding));
    }

    public static void unbindImageView(ImageView imageView) {
        if (imageView == null) {
            return;
        }

        synchronized (imageBindings) {
            imageBindings.remove(imageView);
        }
    }

    public static void addListener(Listener listener) {
        if (listener == null) {
            return;
        }

        synchronized (listeners) {
            listeners.put(listener, Boolean.TRUE);
        }

        listener.onIconPackRuntimeChanged();
    }

    public static void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public static void refreshUi() {
        Map<ImageView, ImageBinding> bindings;
        List<Listener> currentListeners;

        synchronized (imageBindings) {
            bindings = new HashMap<>(imageBindings);
        }
        synchronized (listeners) {
            currentListeners = new ArrayList<>(listeners.keySet());
        }

        for (Map.Entry<ImageView, ImageBinding> entry : bindings.entrySet()) {
            ImageView imageView = entry.getKey();
            ImageBinding binding = entry.getValue();

            if (imageView == null) {
                continue;
            }

            if (binding.animationTarget != null) {
                binding.refreshPending = true;

                continue;
            }

            if (binding.drawable.get() != imageView.getDrawable()) {
                synchronized (imageBindings) {
                    if (imageBindings.get(imageView) == binding) {
                        imageBindings.remove(imageView);
                    }
                }

                continue;
            }

            applyImageResource(imageView, binding.drawableId);

            synchronized (imageBindings) {
                if (imageBindings.get(imageView) == binding) {
                    binding.drawable = new WeakReference<>(imageView.getDrawable());
                }
            }
        }
        for (Listener listener : currentListeners) {
            if (listener != null) {
                listener.onIconPackRuntimeChanged();
            }
        }
    }

    private static void applyImageResource(ImageView imageView, int drawableId) {
        Drawable drawable = resolve(imageView.getResources(), drawableId);

        if (drawable != null) {
            imageView.setImageDrawable(drawable);
        } else {
            imageView.setImageResource(drawableId);
        }
    }

    private static void finishAnimatedBinding(ImageView imageView, ImageBinding binding) {
        synchronized (imageBindings) {
            if (imageBindings.get(imageView) != binding) {
                return;
            }
        }

        Drawable target = binding.animationTarget.get();

        if (target != null && imageView.getDrawable() != target) {
            imageView.postOnAnimation(() -> finishAnimatedBinding(imageView, binding));

            return;
        }

        if (binding.refreshPending || binding.runtimeRevision != revision) {
            applyImageResource(imageView, binding.drawableId);
        }

        synchronized (imageBindings) {
            if (imageBindings.get(imageView) == binding) {
                binding.drawable = new WeakReference<>(imageView.getDrawable());
                binding.animationTarget = null;
                binding.refreshPending = false;
            }
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
        return rebuild(resources, store, installed, activeIds, () -> true);
    }

    static boolean rebuild(
            Resources resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds,
            BooleanSupplier currentRequest) {
        return rebuild(resources, store, installed, activeIds, currentRequest, currentRequest);
    }

    static boolean rebuild(
            Resources resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds,
            BooleanSupplier currentRequest,
            Object publicationLock) {
        if (resources == null || store == null || installed == null || activeIds == null) {
            return publishIfCurrent(Snapshot.empty(), currentRequest, publicationLock);
        }

        HashMap<String, IconPackStore.Installed> byId = new HashMap<>();

        for (IconPackStore.Installed pack : installed) {
            byId.put(pack.manifest.id, pack);
        }

        int densityDpi = resources.getDisplayMetrics().densityDpi;
        int fallbackSize = Math.max(1, Math.round(FALLBACK_ICON_SIZE_DP * densityDpi
                / (float) DisplayMetrics.DENSITY_DEFAULT));
        IconPackAndroidCatalog catalog = new IconPackAndroidCatalog(resources);
        HashMap<Integer, Bitmap> bitmaps = new HashMap<>();
        HashMap<String, Bitmap> namedBitmaps = new HashMap<>();
        HashMap<String, String> sources = new HashMap<>();

        for (String id : activeIds) {
            if (!currentRequest.getAsBoolean()) {
                return false;
            }

            IconPackStore.Installed pack = byId.get(id);

            if (pack == null) {
                continue;
            }

            for (Map.Entry<String, String> entry : pack.manifest.icons.entrySet()) {
                if (!currentRequest.getAsBoolean()) {
                    return false;
                }

                String resourceName = entry.getKey();
                int drawableId = catalog.drawableId(resourceName);

                if (!catalog.contains(resourceName)
                        || namedBitmaps.containsKey(resourceName)
                        || drawableId != 0 && bitmaps.containsKey(drawableId)) {
                    continue;
                }

                int width = fallbackSize;
                int height = fallbackSize;

                if (drawableId != 0) {
                    Drawable original = originalDrawable(resources, drawableId, densityDpi);

                    if (original != null
                            && original.getIntrinsicWidth() > 0
                            && original.getIntrinsicHeight() > 0) {
                        width = original.getIntrinsicWidth();
                        height = original.getIntrinsicHeight();
                    }
                }

                String source = source(pack, resourceName, densityDpi, width, height);

                if (source == null) {
                    continue;
                }

                Bitmap bitmap = cached(source);

                if (bitmap == null) {
                    Drawable drawable = IconPackAndroidDrawableDecoder.resolve(
                            resources,
                            store,
                            pack,
                            resourceName);
                    bitmap = render(drawable, width, height, densityDpi);

                    if (bitmap != null) {
                        cache(source, bitmap);
                    }
                }

                if (bitmap != null) {
                    namedBitmaps.put(resourceName, bitmap);

                    if (drawableId != 0) {
                        bitmaps.put(drawableId, bitmap);
                    }

                    sources.put(
                            resourceName,
                            source);
                }
            }
        }

        if (!currentRequest.getAsBoolean()) {
            return false;
        }

        if (namedBitmaps.isEmpty()) {
            return publishIfCurrent(Snapshot.empty(), currentRequest, publicationLock);
        }

        return publishIfCurrent(
                new Snapshot(densityDpi, bitmaps, namedBitmaps, sources),
                currentRequest,
                publicationLock);
    }

    static boolean clear() {
        return publish(Snapshot.empty());
    }

    static boolean clearIfCurrent(BooleanSupplier currentRequest, Object publicationLock) {
        return publishIfCurrent(Snapshot.empty(), currentRequest, publicationLock);
    }

    private static Drawable originalDrawable(Resources resources, int drawableId, int densityDpi) {
        try {
            if (resources instanceof IconPackResources) {
                return ((IconPackResources) resources).getOriginalDrawableForDensity(
                        drawableId,
                        densityDpi,
                        null);
            }

            return resources.getDrawableForDensity(drawableId, densityDpi, null);
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    private static Bitmap render(
            Drawable drawable,
            int width,
            int height,
            int densityDpi) {
        if (drawable == null || width <= 0 || height <= 0) {
            return null;
        }

        Bitmap result = null;

        try {
            result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            result.setDensity(densityDpi);
            Canvas canvas = new Canvas(result);
            int sourceWidth = drawable.getIntrinsicWidth();
            int sourceHeight = drawable.getIntrinsicHeight();

            if (sourceWidth <= 0 || sourceHeight <= 0) {
                sourceWidth = width;
                sourceHeight = height;
            }

            float scale = Math.min(
                    width / (float) sourceWidth,
                    height / (float) sourceHeight);
            int targetWidth = Math.max(1, Math.round(sourceWidth * scale));
            int targetHeight = Math.max(1, Math.round(sourceHeight * scale));
            int left = (width - targetWidth) / 2;
            int top = (height - targetHeight) / 2;
            drawable.setBounds(left, top, left + targetWidth, top + targetHeight);
            drawable.draw(canvas);

            if (!IconPackAndroidCatalog.hasVisiblePixels(result)) {
                result.recycle();

                return null;
            }

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

    private static String source(
            IconPackStore.Installed pack,
            String resourceName,
            int densityDpi,
            int width,
            int height) {
        String hash = pack.iconHash(resourceName);

        if (hash == null) {
            return null;
        }

        return pack.manifest.id + "\n"
                + pack.generation() + "\n"
                + resourceName + "\n"
                + hash + "\n"
                + densityDpi + "\n"
                + width + "\n"
                + height;
    }

    private static synchronized Bitmap cached(String source) {
        Bitmap bitmap = bitmapCache.get(source);

        if (bitmap != null && bitmap.isRecycled()) {
            bitmapCache.remove(source);
            bitmapCacheBytes -= bitmapBytes(bitmap);

            return null;
        }

        return bitmap;
    }

    private static synchronized void cache(String source, Bitmap bitmap) {
        Bitmap previous = bitmapCache.put(source, bitmap);

        if (previous != null) {
            bitmapCacheBytes -= bitmapBytes(previous);
        }

        bitmapCacheBytes += bitmapBytes(bitmap);

        while (bitmapCacheBytes > MAX_CACHE_BYTES && !bitmapCache.isEmpty()) {
            Map.Entry<String, Bitmap> oldest = bitmapCache.entrySet().iterator().next();
            bitmapCache.remove(oldest.getKey());
            bitmapCacheBytes -= bitmapBytes(oldest.getValue());
        }
    }

    private static int bitmapBytes(Bitmap bitmap) {
        long bytes = (long) bitmap.getWidth() * bitmap.getHeight() * 4L;

        return (int) Math.min(Integer.MAX_VALUE, bytes);
    }

    private static final class ImageBinding {
        private final int drawableId;
        private final long runtimeRevision;
        private WeakReference<Drawable> drawable;
        private WeakReference<Drawable> animationTarget;
        private boolean refreshPending;

        private ImageBinding(int drawableId, Drawable drawable, Drawable animationTarget) {
            this.drawableId = drawableId;
            this.runtimeRevision = revision;
            this.drawable = new WeakReference<>(drawable);
            this.animationTarget = animationTarget == null
                    ? null
                    : new WeakReference<>(animationTarget);
        }
    }

    private static boolean publishIfCurrent(
            Snapshot next,
            BooleanSupplier currentRequest,
            Object publicationLock) {
        synchronized (publicationLock) {
            return currentRequest.getAsBoolean() && publish(next);
        }
    }

    private static synchronized boolean publish(Snapshot next) {
        Snapshot current = snapshot;

        if (current.densityDpi == next.densityDpi && current.sources.equals(next.sources)) {
            return false;
        }

        snapshot = next;
        revision++;

        return true;
    }

    private static final class Snapshot {
        private final int densityDpi;
        private final Map<Integer, Bitmap> bitmaps;
        private final Map<String, Bitmap> namedBitmaps;
        private final Map<String, String> sources;

        private Snapshot(
                int densityDpi,
                Map<Integer, Bitmap> bitmaps,
                Map<String, Bitmap> namedBitmaps,
                Map<String, String> sources) {
            this.densityDpi = densityDpi;
            this.bitmaps = Collections.unmodifiableMap(new HashMap<>(bitmaps));
            this.namedBitmaps = Collections.unmodifiableMap(new HashMap<>(namedBitmaps));
            this.sources = Collections.unmodifiableMap(new HashMap<>(sources));
        }

        private static Snapshot empty() {
            return new Snapshot(
                    0,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap());
        }
    }
}
