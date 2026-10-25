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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

public final class IconPackRuntime {
    private static final int FALLBACK_ICON_SIZE_DP = 24;
    private static final int MAX_CACHE_BYTES = 32 * 1024 * 1024;
    private static final Object DEMAND_LOCK = new Object();
    private static volatile State state = State.empty();
    private static volatile long revision;
    private static volatile Runnable demandListener;
    private static final LinkedHashSet<Demand> demanded = new LinkedHashSet<>();
    private static final LinkedHashSet<Demand> pendingDemands = new LinkedHashSet<>();
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

        Demand demand = Demand.drawable(drawableId);
        State current = state;
        Bitmap bitmap = current.snapshot.bitmap(demand, resources.getDisplayMetrics().densityDpi);

        if (bitmap == null || bitmap.isRecycled()) {
            recordDemand(demand);

            return null;
        }

        try {
            return new BitmapDrawable(resources, bitmap);
        } catch (RuntimeException | OutOfMemoryError error) {
            recordDemand(demand);

            return null;
        }
    }

    public static Drawable resolve(Resources resources, String resourceName) {
        if (resources == null || !IconPackManifest.isValidResourceName(resourceName)) {
            return null;
        }

        Demand demand = Demand.named(resourceName);
        State current = state;
        Bitmap bitmap = current.snapshot.bitmap(demand, resources.getDisplayMetrics().densityDpi);

        if (bitmap == null || bitmap.isRecycled()) {
            recordDemand(demand);

            return null;
        }

        try {
            return new BitmapDrawable(resources, bitmap);
        } catch (RuntimeException | OutOfMemoryError error) {
            recordDemand(demand);

            return null;
        }
    }

    private static void recordDemand(Demand demand) {
        Runnable listener = null;

        synchronized (DEMAND_LOCK) {
            State current = state;
            demanded.add(demand);

            if (current.snapshot.bitmaps.containsKey(demand) || current.negatives.contains(demand)) {
                return;
            }

            if (pendingDemands.add(demand)) {
                listener = current.plan == null ? null : demandListener;
            }
        }

        if (listener != null) {
            listener.run();
        }
    }

    static void setDemandListener(Runnable listener) {
        demandListener = listener;
    }

    static boolean hasPendingDemands() {
        synchronized (DEMAND_LOCK) {
            return state.plan != null && !pendingDemands.isEmpty();
        }
    }

    static int pendingDemandCount() {
        synchronized (DEMAND_LOCK) {
            return pendingDemands.size();
        }
    }

    static int bitmapCacheBytes() {
        synchronized (IconPackRuntime.class) {
            return bitmapCacheBytes;
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
        return rebuild(
                resources,
                store,
                installed,
                activeIds,
                0,
                currentRequest,
                publicationLock);
    }

    static boolean rebuild(
            Resources resources,
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds,
            long requestId,
            BooleanSupplier currentRequest,
            Object publicationLock) {
        if (resources == null || store == null || installed == null || activeIds == null) {
            return clearIfCurrent(currentRequest, publicationLock);
        }

        HashMap<String, IconPackStore.Installed> byId = new HashMap<>();

        for (IconPackStore.Installed pack : installed) {
            byId.put(pack.manifest.id, pack);
        }

        ArrayList<IconPackStore.Installed> active = new ArrayList<>();

        for (String id : activeIds) {
            IconPackStore.Installed pack = byId.get(id);

            if (pack != null) {
                active.add(pack);
            }
        }

        Plan plan = new Plan(resources, store, active, requestId);
        List<Demand> requested;

        synchronized (DEMAND_LOCK) {
            requested = new ArrayList<>(demanded);
        }

        Decoded decoded = decode(plan, requested, currentRequest);

        if (decoded == null) {
            return false;
        }

        synchronized (publicationLock) {
            if (!currentRequest.getAsBoolean()) {
                return false;
            }

            boolean changed = publish(new State(
                    plan,
                    decoded.resolutions,
                    decoded.negatives,
                    snapshot(plan, decoded.resolutions)));

            synchronized (DEMAND_LOCK) {
                pendingDemands.removeAll(requested);
            }

            return changed;
        }
    }

    static boolean decodePending(BooleanSupplier currentRequest, Object publicationLock) {
        return decodePending(0, currentRequest, publicationLock);
    }

    static boolean decodePending(
            long requestId,
            BooleanSupplier currentRequest,
            Object publicationLock) {
        State current = state;
        Plan plan = current.plan;

        if (plan == null || plan.requestId != requestId) {
            return false;
        }

        List<Demand> requested;

        synchronized (DEMAND_LOCK) {
            if (pendingDemands.isEmpty()) {
                return false;
            }

            requested = new ArrayList<>(pendingDemands);
        }

        Decoded decoded = decode(plan, requested, currentRequest);

        if (decoded == null) {
            return false;
        }

        synchronized (publicationLock) {
            if (!currentRequest.getAsBoolean()
                    || state.plan != plan
                    || plan.requestId != requestId) {
                return false;
            }

            HashMap<Demand, Resolution> resolutions = new HashMap<>(state.resolutions);
            HashSet<Demand> negatives = new HashSet<>(state.negatives);
            resolutions.putAll(decoded.resolutions);
            negatives.removeAll(requested);
            negatives.addAll(decoded.negatives);
            State next = new State(plan, resolutions, negatives, snapshot(plan, resolutions));
            boolean changed = publish(next);

            synchronized (DEMAND_LOCK) {
                pendingDemands.removeAll(requested);
            }

            return changed;
        }
    }

    static boolean clear() {
        return clearIfCurrent(() -> true, IconPackRuntime.class);
    }

    static boolean clearIfCurrent(BooleanSupplier currentRequest, Object publicationLock) {
        synchronized (publicationLock) {
            if (!currentRequest.getAsBoolean()) {
                return false;
            }

            synchronized (DEMAND_LOCK) {
                pendingDemands.addAll(demanded);
            }

            return publish(State.empty());
        }
    }

    private static Decoded decode(
            Plan plan,
            List<Demand> requested,
            BooleanSupplier currentRequest) {
        HashMap<Demand, Resolution> resolutions = new HashMap<>();
        HashSet<Demand> negatives = new HashSet<>();

        for (Demand demand : requested) {
            if (!currentRequest.getAsBoolean()) {
                return null;
            }

            Resolution resolution = decode(plan, demand, currentRequest);

            if (resolution == null) {
                if (!currentRequest.getAsBoolean()) {
                    return null;
                }

                negatives.add(demand);
            } else {
                resolutions.put(demand, resolution);
            }
        }

        return new Decoded(resolutions, negatives);
    }

    private static Resolution decode(
            Plan plan,
            Demand demand,
            BooleanSupplier currentRequest) {
        String resourceName = demand.resourceName;
        int drawableId = demand.drawableId;

        if (resourceName == null) {
            resourceName = plan.catalog.resourceName(drawableId);
        } else {
            drawableId = plan.catalog.drawableId(resourceName);
        }

        if (resourceName == null || !plan.catalog.contains(resourceName)) {
            return null;
        }

        int width = plan.fallbackSize;
        int height = plan.fallbackSize;

        if (drawableId != 0) {
            Drawable original = originalDrawable(plan.resources, drawableId, plan.densityDpi);

            if (original != null
                    && original.getIntrinsicWidth() > 0
                    && original.getIntrinsicHeight() > 0) {
                width = original.getIntrinsicWidth();
                height = original.getIntrinsicHeight();
            }
        }

        for (IconPackStore.Installed pack : plan.active) {
            if (!currentRequest.getAsBoolean()) {
                return null;
            }
            if (!pack.manifest.icons.containsKey(resourceName)) {
                continue;
            }

            String source = source(pack, resourceName, plan.densityDpi, width, height);

            if (source == null) {
                continue;
            }

            Bitmap bitmap = cached(source);

            if (bitmap == null) {
                Drawable drawable = IconPackAndroidDrawableDecoder.resolve(
                        plan.resources,
                        plan.store,
                        pack,
                        resourceName);
                bitmap = render(drawable, width, height, plan.densityDpi);

                if (bitmap != null) {
                    cache(source, bitmap);
                }
            }

            if (bitmap != null && isCached(source, bitmap)) {
                return new Resolution(source);
            }
        }

        return null;
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

    private static synchronized boolean isCached(String source, Bitmap bitmap) {
        return bitmapCache.get(source) == bitmap;
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

    private static synchronized Snapshot snapshot(
            Plan plan,
            Map<Demand, Resolution> resolutions) {
        HashMap<Demand, Bitmap> bitmaps = new HashMap<>();
        HashMap<String, String> sources = new HashMap<>();

        for (Map.Entry<Demand, Resolution> entry : resolutions.entrySet()) {
            Resolution resolution = entry.getValue();
            Bitmap cached = bitmapCache.get(resolution.source);

            if (cached != null && !cached.isRecycled()) {
                bitmaps.put(entry.getKey(), cached);
                sources.put(entry.getKey().key, resolution.source);
            }
        }

        return new Snapshot(plan.densityDpi, bitmaps, sources);
    }

    private static synchronized boolean publish(State next) {
        State current = state;
        boolean changed = !current.snapshot.sameContent(next.snapshot);
        state = next;

        if (changed) {
            revision++;
        }

        return changed;
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

    private static final class Plan {
        private final Resources resources;
        private final IconPackStore store;
        private final List<IconPackStore.Installed> active;
        private final IconPackAndroidCatalog catalog;
        private final int densityDpi;
        private final int fallbackSize;
        private final long requestId;

        private Plan(
                Resources resources,
                IconPackStore store,
                List<IconPackStore.Installed> active,
                long requestId) {
            this.resources = resources;
            this.store = store;
            this.active = Collections.unmodifiableList(new ArrayList<>(active));
            this.catalog = new IconPackAndroidCatalog(resources);
            this.densityDpi = resources.getDisplayMetrics().densityDpi;
            this.fallbackSize = Math.max(1, Math.round(FALLBACK_ICON_SIZE_DP * densityDpi
                    / (float) DisplayMetrics.DENSITY_DEFAULT));
            this.requestId = requestId;
        }
    }

    private static final class State {
        private final Plan plan;
        private final Map<Demand, Resolution> resolutions;
        private final Set<Demand> negatives;
        private final Snapshot snapshot;

        private State(
                Plan plan,
                Map<Demand, Resolution> resolutions,
                Set<Demand> negatives,
                Snapshot snapshot) {
            this.plan = plan;
            this.resolutions = Collections.unmodifiableMap(new HashMap<>(resolutions));
            this.negatives = Collections.unmodifiableSet(new HashSet<>(negatives));
            this.snapshot = snapshot;
        }

        private static State empty() {
            return new State(
                    null,
                    Collections.emptyMap(),
                    Collections.emptySet(),
                    Snapshot.empty());
        }
    }

    private static final class Demand {
        private final int drawableId;
        private final String resourceName;
        private final String key;

        private Demand(int drawableId, String resourceName, String key) {
            this.drawableId = drawableId;
            this.resourceName = resourceName;
            this.key = key;
        }

        private static Demand drawable(int drawableId) {
            return new Demand(drawableId, null, "drawable:" + drawableId);
        }

        private static Demand named(String resourceName) {
            return new Demand(0, resourceName, "name:" + resourceName);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Demand && key.equals(((Demand) other).key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    private static final class Resolution {
        private final String source;

        private Resolution(String source) {
            this.source = source;
        }
    }

    private static final class Decoded {
        private final Map<Demand, Resolution> resolutions;
        private final Set<Demand> negatives;

        private Decoded(
                Map<Demand, Resolution> resolutions,
                Set<Demand> negatives) {
            this.resolutions = resolutions;
            this.negatives = negatives;
        }
    }

    private static final class Snapshot {
        private final int densityDpi;
        private final Map<Demand, Bitmap> bitmaps;
        private final Map<String, String> sources;

        private Snapshot(
                int densityDpi,
                Map<Demand, Bitmap> bitmaps,
                Map<String, String> sources) {
            this.densityDpi = densityDpi;
            this.bitmaps = Collections.unmodifiableMap(new HashMap<>(bitmaps));
            this.sources = Collections.unmodifiableMap(new HashMap<>(sources));
        }

        private Bitmap bitmap(Demand demand, int requestedDensityDpi) {
            return densityDpi == requestedDensityDpi ? bitmaps.get(demand) : null;
        }

        private boolean sameContent(Snapshot other) {
            return sources.equals(other.sources)
                    && (sources.isEmpty() || densityDpi == other.densityDpi);
        }

        private static Snapshot empty() {
            return new Snapshot(0, Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
