package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import org.telegram.messenger.SvgHelper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

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

        byte[] raster = "raster:24:32".getBytes(StandardCharsets.UTF_8);

        require(catalog.decodesRaster(raster, "icon.png", 24, 32));
        require(catalog.decodesRaster(raster, "icon.jpg", 24, 32));
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
        require(!catalog.decodesRaster(
                "raster:2049:2048".getBytes(StandardCharsets.UTF_8),
                "icon.png",
                2049,
                2048));
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

        byte[] svg = "<svg viewBox=\"0 0 24 24\"/>".getBytes(StandardCharsets.UTF_8);

        require(catalog.decodesSvg(svg, "icon.svg"));
        require(!catalog.decodesSvg(new byte[] {(byte) 0xc3, 0x28}, "icon.svg"));
        require(!catalog.decodesSvg("unsupported".getBytes(StandardCharsets.UTF_8), "icon.svg"));
        require(!catalog.decodesSvg("runtime".getBytes(StandardCharsets.UTF_8), "icon.svg"));
        require(!catalog.decodesSvg("out-of-memory".getBytes(StandardCharsets.UTF_8), "icon.svg"));

        Drawable rasterDrawable = IconPackAndroidDrawableDecoder.decodeVerified(
                resources,
                raster,
                "icon.webp");

        require(rasterDrawable instanceof BitmapDrawable);
        require(IconPackAndroidDrawableDecoder.decodeVerified(resources, raster, "icon.jpeg")
                instanceof BitmapDrawable);
        require(!((BitmapDrawable) rasterDrawable).getBitmap().isRecycled());
        require(IconPackAndroidDrawableDecoder.decodeVerified(resources, svg, "icon.svg")
                instanceof SvgHelper.SvgDrawable);
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
        IconPackStore.Installed first = new IconPackStore.Installed(
                new IconPackManifest(
                        "first.pack",
                        Collections.singletonMap("msg_add", "first.png")),
                "first-generation");
        IconPackStore.Installed second = new IconPackStore.Installed(
                new IconPackManifest(
                        "second.pack",
                        Collections.singletonMap("msg_add", "second.png")),
                "second-generation");
        store.setPayload(first, raster);
        store.setPayload(second, "raster:32:24".getBytes(StandardCharsets.UTF_8));

        long initialRevision = IconPackRuntime.revision();
        require(!IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.emptyList()));
        require(IconPackRuntime.revision() == initialRevision);
        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Arrays.asList("first.pack", "second.pack")));
        require(IconPackRuntime.revision() == initialRevision + 1);
        require(store.lastResolved == first);

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
        require(IconPackRuntime.resolve(resources, 13) == null);

        IconPackResources wrapped = new IconPackResources(resources);
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

        require(!IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Arrays.asList("first.pack", "second.pack")));
        require(IconPackRuntime.revision() == initialRevision + 1);
        require(Bitmap.lastCreated.isRecycled());

        require(IconPackRuntime.rebuild(
                resources,
                store,
                Arrays.asList(first, second),
                Collections.singletonList("second.pack")));
        require(store.lastResolved == second);
        require(!((BitmapDrawable) firstDrawable).getBitmap().isRecycled());

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

        require(IconPackRuntime.clear());
        require(IconPackRuntime.revision() == initialRevision + 4);
        require(IconPackRuntime.resolve(resources, 1) == null);
    }

    private static void require(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }
}
