package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.caverock.androidsvg.SVG;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IconPackAndroidCheck {
    private static final String[] NAMES = {
            "msg_add",
            "msg_copy",
            "msg_delete",
            "msg_download",
            "msg_edit",
            "msg_forward",
            "msg_gallery",
            "msg_search",
            "msg_select",
            "msg_send",
            "msg_settings",
            "msg_share"
    };

    public static void main(String[] args) {
        Resources resources = new Resources();
        IconPackAndroidCatalog catalog = new IconPackAndroidCatalog(resources);

        for (int i = 0; i < NAMES.length; i++) {
            require(catalog.contains(NAMES[i]));
            require(catalog.drawableId(NAMES[i]) == i + 1);
            require(catalog.resourceName(i + 1).equals(NAMES[i]));
        }

        require(!catalog.contains("msg_unknown"));
        require(catalog.contains("chats_saved"));
        require(catalog.drawableId("chats_saved") == 13);
        require(catalog.resourceName(13).equals("chats_saved"));
        require(catalog.drawableId("msg_unknown") == 0);
        require(catalog.resourceName(0) == null);
        require(catalog.resourceName(15) == null);
        require(catalog.contains("boosts"));
        require(catalog.drawableId("boosts") == 0);
        require(catalog.contains("fab_compose_small"));
        require(catalog.drawableId("fab_compose_small") == 0);
        require(catalog.contains("instant_camera"));
        require(catalog.drawableId("instant_camera") == 0);
        require(catalog.contains("msg2_proxy_off"));
        require(catalog.drawableId("msg2_proxy_off") == 0);
        require(catalog.contains("msg2_proxy_on"));
        require(catalog.drawableId("msg2_proxy_on") == 0);
        require(catalog.contains("tabs_chats_24"));
        require(catalog.drawableId("tabs_chats_24") == 0);
        require(catalog.contains("tabs_calls_active_24"));

        byte[] raster = "raster:24:32".getBytes(StandardCharsets.UTF_8);

        require(catalog.decodesRaster(raster, "icon.png", 24, 32));
        require(catalog.decodesRaster(raster, "icon.jpg", 24, 32));
        require(Bitmap.lastCreated.isRecycled());
        require(!catalog.decodesRaster(
                "transparent:24:32".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                24,
                32));
        require(Bitmap.lastCreated.isRecycled());
        require(!catalog.decodesRaster(raster, "icon.png", 32, 24));
        require(!catalog.decodesRaster(
                "decode-null:24:32".getBytes(StandardCharsets.UTF_8),
                "icon.webp",
                24,
                32));
        require(!catalog.decodesRaster(
                "decoded-mismatch:24:32".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                24,
                32));
        require(Bitmap.lastCreated.isRecycled());
        require(catalog.decodesRaster(
                "raster:2048:2048".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                2048,
                2048));
        require(catalog.decodesRaster(
                "raster:1440:2088".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                1440,
                2088));
        require(!catalog.decodesRaster(
                "raster:2049:2049".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                2049,
                2049));
        require(!catalog.decodesRaster(
                "raster:4097:1".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                4097,
                1));
        require(!catalog.decodesRaster(
                "bounds-runtime:24:32".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                24,
                32));
        require(!catalog.decodesRaster(
                "bounds-oom:24:32".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                24,
                32));
        require(!catalog.decodesRaster(
                "decode-runtime:24:32".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                24,
                32));
        require(!catalog.decodesRaster(
                "decode-oom:24:32".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                24,
                32));
        require(!catalog.decodesRaster(
                new byte[(int) IconPackArchiveValidator.MAX_ICON_BYTES + 1],
                "icon.png",
                24,
                32));

        byte[] svg = "<svg viewBox=\"0 0 24 24\"><path d=\"M1 1h2v2z\"/></svg>"
                .getBytes(StandardCharsets.UTF_8);

        require(catalog.decodesSvg(svg, "icon.svg"));
        require(SVG.lastDocumentWidth == 256);
        require(SVG.lastDocumentHeight == 256);
        byte[] maskedSvg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">"
                + "<defs><linearGradient id=\"g\"><stop stop-color=\"white\"/></linearGradient>"
                + "<mask id=\"m\"><path d=\"M0 0h24v24z\" fill=\"white\"/></mask></defs>"
                + "<g mask=\"url(#m)\"><path fill=\"url(#g)\" d=\"M1 1h2v2z\"/></g></svg>")
                .getBytes(StandardCharsets.UTF_8);

        require(catalog.decodesSvg(maskedSvg, "icon.svg"));
        require(SVG.lastXml.contains("<linearGradient"));
        require(SVG.lastXml.contains("<mask"));
        require(SVG.lastXml.contains("mask=\"url(#m)\""));
        require(!catalog.decodesSvg(
                "<svg viewBox=\"0 0 24 24\"><path transparent=\"true\"/></svg>"
                        .getBytes(StandardCharsets.UTF_8),
                "icon.svg"));
        require(!catalog.decodesSvg(new byte[] {(byte) 0xc3, 0x28}, "icon.svg"));
        require(!catalog.decodesSvg("parse-error".getBytes(StandardCharsets.UTF_8), "icon.svg"));
        require(!catalog.decodesSvg("render-error".getBytes(StandardCharsets.UTF_8), "icon.svg"));
        require(!catalog.decodesSvg("out-of-memory".getBytes(StandardCharsets.UTF_8), "icon.svg"));
        require(!catalog.decodesSvg("stack-overflow".getBytes(StandardCharsets.UTF_8), "icon.svg"));

        Drawable rasterDrawable = IconPackAndroidDrawableDecoder.decodeVerified(
                resources,
                raster,
                "icon.webp");

        require(rasterDrawable instanceof BitmapDrawable);
        require(IconPackAndroidDrawableDecoder.decodeVerified(resources, raster, "icon.jpeg")
                instanceof BitmapDrawable);
        require(!((BitmapDrawable) rasterDrawable).getBitmap().isRecycled());
        require(IconPackAndroidDrawableDecoder.decodeVerified(resources, svg, "icon.svg")
                instanceof BitmapDrawable);
        require(IconPackAndroidDrawableDecoder.decodeVerified(resources, svg, "icon.txt") == null);
        require(IconPackAndroidDrawableDecoder.decodeVerified(null, svg, "icon.svg") == null);

        BitmapDrawable.throwRuntime = true;
        require(IconPackAndroidDrawableDecoder.decodeVerified(resources, raster, "icon.png") == null);
        require(Bitmap.lastCreated.isRecycled());

        BitmapDrawable.throwOutOfMemory = true;
        require(IconPackAndroidDrawableDecoder.decodeVerified(resources, raster, "icon.png") == null);
        require(Bitmap.lastCreated.isRecycled());

        IconPackStore store = new IconPackStore();
        IconPackStore.Installed installed = new IconPackStore.Installed(new IconPackManifest(
                Collections.singletonMap("msg_add", "icon.png")));
        store.setPayload(raster);

        require(IconPackAndroidDrawableDecoder.resolve(
                resources,
                store,
                installed,
                "msg_add") instanceof BitmapDrawable);
        require(IconPackAndroidDrawableDecoder.resolve(
                resources,
                store,
                installed,
                "msg_unknown") == null);

        IconPackRuntime.clear();
        IconPackResources wrapped = new IconPackResources(resources);
        require(wrapped.getOriginalDrawableForDensity(11, 160, null).getIntrinsicWidth() == 32);
        require(wrapped.getOriginalDrawableForDensity(11, 160, null).getIntrinsicHeight() == 18);
        Map<String, String> firstIcons = new LinkedHashMap<>();
        firstIcons.put("msg_add", "first.png");
        firstIcons.put("msg_copy", "first.png");
        firstIcons.put("msg_share", "first.png");
        firstIcons.put("msg_settings", "first.png");
        firstIcons.put("boosts", "first.png");
        firstIcons.put("tabs_chats_24", "first.png");
        IconPackStore.Installed first = new IconPackStore.Installed(
                new IconPackManifest("first.pack", firstIcons),
                "first-generation");
        IconPackStore.Installed second = new IconPackStore.Installed(
                new IconPackManifest(
                        "second.pack",
                        Collections.singletonMap("msg_add", "second.png")),
                "second-generation");
        store.setPayload(first, raster);
        store.setPayload(second, "raster:32:24".getBytes(StandardCharsets.UTF_8));

        IconPackStore.Installed transparent = new IconPackStore.Installed(
                new IconPackManifest(
                        "transparent.pack",
                        Collections.singletonMap("msg_add", "transparent.svg")),
                "transparent-generation");
        IconPackStore.Installed fallback = new IconPackStore.Installed(
                new IconPackManifest(
                        "fallback.pack",
                        Collections.singletonMap("msg_add", "fallback.png")),
                "fallback-generation");
        store.setPayload(
                transparent,
                "<svg viewBox=\"0 0 24 24\"><path transparent=\"true\"/></svg>"
                        .getBytes(StandardCharsets.UTF_8));
        store.setPayload(fallback, "raster:32:24".getBytes(StandardCharsets.UTF_8));

        store.resolveCount = 0;
        long initialRevision = IconPackRuntime.revision();
        require(!IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Arrays.asList("first.pack", "second.pack")));
        require(store.resolveCount == 0);
        require(IconPackRuntime.revision() == initialRevision);
        require(IconPackRuntime.resolve(resources, 1) == null);
        require(IconPackRuntime.resolve(resources, 11) == null);
        require(IconPackRuntime.resolve(resources, "boosts") == null);
        require(IconPackRuntime.resolve(resources, "tabs_chats_24") == null);
        require(IconPackRuntime.pendingDemandCount() == 4);
        require(store.resolveCount == 0);
        require(IconPackRuntime.decodePending(() -> true, new Object()));
        require(IconPackRuntime.revision() == initialRevision + 1);
        require(IconPackRuntime.pendingDemandCount() == 0);
        require(store.resolveCount == 4);

        Drawable firstDrawable = IconPackRuntime.resolve(resources, 1);
        Drawable secondDrawable = IconPackRuntime.resolve(resources, 1);
        require(firstDrawable instanceof BitmapDrawable);
        require(secondDrawable instanceof BitmapDrawable);
        require(firstDrawable != secondDrawable);
        require(((BitmapDrawable) firstDrawable).getBitmap()
                == ((BitmapDrawable) secondDrawable).getBitmap());
        require(((BitmapDrawable) firstDrawable).getBitmap().getWidth() == 24);
        require(((BitmapDrawable) firstDrawable).getBitmap().getHeight() == 24);
        require(((BitmapDrawable) firstDrawable).getBitmap().getDensity() == 160);
        Drawable settingsDrawable = IconPackRuntime.resolve(resources, 11);
        require(settingsDrawable instanceof BitmapDrawable);
        require(((BitmapDrawable) settingsDrawable).getBitmap().getWidth() == 32);
        require(((BitmapDrawable) settingsDrawable).getBitmap().getHeight() == 18);
        require(IconPackRuntime.resolve(resources, "boosts") instanceof BitmapDrawable);
        require(IconPackRuntime.resolve(resources, "tabs_chats_24") instanceof BitmapDrawable);
        require(IconPackRuntime.resolve(resources, "tabs_missing_24") == null);
        require(IconPackRuntime.resolve(resources, 13) == null);
        require(IconPackRuntime.pendingDemandCount() == 2);
        require(!IconPackRuntime.decodePending(() -> true, new Object()));
        require(IconPackRuntime.pendingDemandCount() == 0);
        int negativeResolveCount = store.resolveCount;
        require(IconPackRuntime.resolve(resources, "tabs_missing_24") == null);
        require(IconPackRuntime.resolve(resources, 13) == null);
        require(IconPackRuntime.pendingDemandCount() == 0);
        require(store.resolveCount == negativeResolveCount);
        require(!IconPackRuntime.decodePending(() -> true, new Object()));
        require(IconPackRuntime.revision() == initialRevision + 1);

        require(wrapped.getDrawableForDensity(1, 0, null) instanceof BitmapDrawable);
        require(!(wrapped.getDrawableForDensity(1, 320, null) instanceof BitmapDrawable));
        require(resources.lastDrawableId == 1);
        require(!(wrapped.getDrawableForDensity(13, 0, null) instanceof BitmapDrawable));
        require(resources.lastDrawableId == 13);

        ImageView imageView = new ImageView(resources);
        IconPackRuntime.setImageResource(imageView, 1);
        require(imageView.drawable instanceof BitmapDrawable);
        IconPackRuntime.setImageResource(imageView, 13);
        require(imageView.drawable == null);
        require(imageView.resourceId == 13);

        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(transparent, fallback),
                Arrays.asList("transparent.pack", "fallback.pack")));
        require(IconPackRuntime.resolve(resources, 1) instanceof BitmapDrawable);
        require(store.lastResolved == fallback);
        require(IconPackRuntime.revision() == initialRevision + 2);

        int[] refreshes = {0};
        IconPackRuntime.Listener listener = () -> refreshes[0]++;
        IconPackRuntime.addListener(listener);
        require(refreshes[0] == 1);
        IconPackRuntime.setImageResource(imageView, 1);
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("second.pack")));
        require(store.lastResolved == second);
        require(!((BitmapDrawable) firstDrawable).getBitmap().isRecycled());
        require(IconPackRuntime.resolve(resources, "tabs_chats_24") == null);
        IconPackRuntime.refreshUi();
        require(refreshes[0] == 2);
        require(imageView.drawable instanceof BitmapDrawable);
        require(((BitmapDrawable) imageView.drawable).getBitmap()
                == ((BitmapDrawable) IconPackRuntime.resolve(resources, 1)).getBitmap());
        IconPackRuntime.removeListener(listener);

        Drawable customDrawable = new Drawable() {
        };
        imageView.setImageDrawable(customDrawable);
        IconPackRuntime.refreshUi();
        require(imageView.drawable == customDrawable);

        IconPackRuntime.trackImageResource(imageView, 1);
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("first.pack")));
        IconPackRuntime.refreshUi();
        require(imageView.drawable instanceof BitmapDrawable);

        IconPackRuntime.unbindImageView(imageView);
        customDrawable = new Drawable() {
        };
        imageView.setImageDrawable(customDrawable);
        IconPackRuntime.refreshUi();
        require(imageView.drawable == customDrawable);

        IconPackStore.Installed updatedFirst = new IconPackStore.Installed(
                new IconPackManifest("first.pack", firstIcons),
                "updated-generation");
        store.setPayload(updatedFirst, "raster:32:24".getBytes(StandardCharsets.UTF_8));
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(updatedFirst, second),
                Collections.singletonList("first.pack")));
        Bitmap updatedBitmap = ((BitmapDrawable) IconPackRuntime.resolve(resources, 1)).getBitmap();
        require(updatedBitmap != ((BitmapDrawable) firstDrawable).getBitmap());
        require(updatedBitmap.getWidth() == 24);
        require(updatedBitmap.getHeight() == 24);

        require(IconPackRuntime.resolve(resources, 2) == null);
        require(IconPackRuntime.decodePending(() -> true, new Object()));
        require(IconPackRuntime.resolve(resources, 2) instanceof BitmapDrawable);
        require(IconPackRuntime.clear());
        require(IconPackRuntime.resolve(resources, 1) == null);
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("second.pack")));
        require(IconPackRuntime.resolve(resources, 1) instanceof BitmapDrawable);

        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("first.pack")));
        require(IconPackRuntime.resolve(resources, 2) instanceof BitmapDrawable);

        require(!IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("first.pack"),
                42,
                () -> true,
                new Object()));
        require(IconPackRuntime.resolve(resources, 12) == null);
        require(IconPackRuntime.pendingDemandCount() == 1);
        int staleDrainResolveCount = store.resolveCount;
        require(!IconPackRuntime.decodePending(41, () -> true, new Object()));
        require(IconPackRuntime.pendingDemandCount() == 1);
        require(store.resolveCount == staleDrainResolveCount);
        require(IconPackRuntime.decodePending(42, () -> true, new Object()));
        require(IconPackRuntime.resolve(resources, 12) instanceof BitmapDrawable);

        IconPackRuntime.clear();
        require(IconPackRuntime.resolve(resources, 2) == null);
        require(IconPackRuntime.pendingDemandCount() > 0);
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("first.pack")));
        require(IconPackRuntime.resolve(resources, 2) instanceof BitmapDrawable);

        IconPackRuntime.clear();
        require(IconPackRuntime.resolve(resources, 2) == null);
        IconPackStore.Installed staleFirst = new IconPackStore.Installed(
                new IconPackManifest("first.pack", firstIcons),
                "stale-generation");
        store.setPayload(staleFirst, raster);
        final boolean[] currentRequest = {true};
        store.resolveHook = () -> currentRequest[0] = false;
        require(!IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(staleFirst, second),
                Collections.singletonList("first.pack"),
                () -> currentRequest[0],
                new Object()));
        require(IconPackRuntime.resolve(resources, 2) == null);
        require(IconPackRuntime.pendingDemandCount() > 0);
        store.resolveHook = null;
        currentRequest[0] = true;
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("first.pack")));
        require(IconPackRuntime.resolve(resources, 2) instanceof BitmapDrawable);

        resources.getDisplayMetrics().densityDpi = 320;
        require(IconPackRuntime.resolve(resources, 1) == null);
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("second.pack")));
        Bitmap densityBitmap = ((BitmapDrawable) IconPackRuntime.resolve(resources, 1)).getBitmap();
        require(densityBitmap.getWidth() == 48);
        require(densityBitmap.getHeight() == 48);
        require(densityBitmap.getDensity() == 320);
        require(IconPackRuntime.bitmapCacheBytes() <= 32 * 1024 * 1024);

        require(IconPackRuntime.clear());
        require(IconPackRuntime.resolve(resources, 1) == null);
    }

    private static void require(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }
}
