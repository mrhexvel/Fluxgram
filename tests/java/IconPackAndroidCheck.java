package org.telegram.messenger.fluxgram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.SvgHelper;

import java.nio.charset.StandardCharsets;
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
        IconPackAndroidCatalog catalog = new IconPackAndroidCatalog();

        for (int i = 0; i < NAMES.length; i++) {
            require(catalog.contains(NAMES[i]));
            require(catalog.drawableId(NAMES[i]) == i + 1);
        }

        require(!catalog.contains("msg_unknown"));
        require(catalog.drawableId("msg_unknown") == 0);

        byte[] raster = "raster:24:32".getBytes(StandardCharsets.UTF_8);

        require(catalog.decodesRaster(raster, "icon.png", 24, 32));
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

        Resources resources = new Resources();
        Drawable rasterDrawable = IconPackAndroidDrawableDecoder.decodeVerified(
                resources,
                raster,
                "icon.webp");

        require(rasterDrawable instanceof BitmapDrawable);
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
    }

    private static void require(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }
}
