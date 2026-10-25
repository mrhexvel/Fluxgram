package android.content.res;

import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import java.util.HashMap;
import java.util.Map;

public class Resources {
    public static class Theme {
    }

    public static class NotFoundException extends RuntimeException {
    }

    private final DisplayMetrics displayMetrics = new DisplayMetrics();
    private final AssetManager assets;
    private final Configuration configuration;
    private final Map<String, Integer> identifiers = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();

    public int lastDrawableId;

    public Resources() {
        this(new AssetManager(), new DisplayMetrics(), new Configuration());
    }

    public Resources(AssetManager assets, DisplayMetrics metrics, Configuration configuration) {
        this.assets = assets;
        this.configuration = configuration;
        this.displayMetrics.densityDpi = metrics.densityDpi;

        register("msg_add", 1);
        register("msg_copy", 2);
        register("msg_delete", 3);
        register("msg_download", 4);
        register("msg_edit", 5);
        register("msg_forward", 6);
        register("msg_gallery", 7);
        register("msg_search", 8);
        register("msg_select", 9);
        register("msg_send", 10);
        register("msg_settings", 11);
        register("msg_share", 12);
        register("chats_saved", 13);
        register("filled_understood", 14);
    }

    public DisplayMetrics getDisplayMetrics() {
        return displayMetrics;
    }

    public AssetManager getAssets() {
        return assets;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public int getIdentifier(String name, String type, String packageName) {
        if (!"drawable".equals(type) || !"org.fluxgram.messenger".equals(packageName)) {
            return 0;
        }

        Integer identifier = identifiers.get(name);

        return identifier == null ? 0 : identifier;
    }

    public String getResourcePackageName(int id) {
        require(id);

        return "org.fluxgram.messenger";
    }

    public String getResourceTypeName(int id) {
        require(id);

        return "drawable";
    }

    public String getResourceEntryName(int id) {
        require(id);

        return names.get(id);
    }

    public Drawable getDrawableForDensity(int id, int density, Theme theme) {
        require(id);
        lastDrawableId = id;

        return new Drawable() {
        };
    }

    private void register(String name, int id) {
        identifiers.put(name, id);
        names.put(id, name);
    }

    private void require(int id) {
        if (!names.containsKey(id)) {
            throw new NotFoundException();
        }
    }
}
