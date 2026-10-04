package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginIcons;
import org.telegram.messenger.plugins.packages.PluginPackageStore;
import org.telegram.messenger.plugins.packages.PluginPackageValidator;
import org.telegram.ui.ActionBar.Theme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FluxGramPluginIconView extends BackupImageView {
    private static final ExecutorService loader = Executors.newSingleThreadExecutor();
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    public FluxGramPluginIconView(Context context, Theme.ResourcesProvider provider, String id, String name) {
        super(context);
        setRoundRadius(AndroidUtilities.dp(14));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        AvatarDrawable placeholder = new AvatarDrawable();
        placeholder.setRoundRadius(AndroidUtilities.dp(14));
        String initial = name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0)));
        placeholder.setInfo(id.hashCode(), name, null, initial);
        setImageDrawable(placeholder);
    }

    public void load(Plugin plugin, Theme.ResourcesProvider provider) {
        int resource = plugin.getIconResource();

        if (resource != 0) {
            int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, provider);
            setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.multAlpha(color, 0.14f)));
            Drawable drawable = getResources().getDrawable(resource).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            setImageDrawable(drawable);
            setSize(AndroidUtilities.dp(30), AndroidUtilities.dp(30));
            return;
        }

        File file = plugin.getIconFile();

        if (file == null) {
            return;
        }

        String key = file.getAbsolutePath() + ":" + plugin.getVersion() + ":" + System.identityHashCode(plugin);
        Bitmap cached = cache.get(key);

        if (cached != null) {
            setImageBitmap(cached);
            return;
        }

        loader.execute(() -> {
            try {
                if (!file.getCanonicalFile().equals(file.getAbsoluteFile())) {
                    return;
                }

                byte[] data;

                try (FileInputStream in = new FileInputStream(file);
                        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    PluginPackageStore.copy(in, out, PluginPackageValidator.MAX_ICON_BYTES);
                    data = out.toByteArray();
                }

                Bitmap bitmap = PluginIcons.decode(data, file.getName());
                cache.put(key, bitmap);
                AndroidUtilities.runOnUIThread(() -> setImageBitmap(bitmap));
            } catch (Exception error) {
                FileLog.e(error);
            }
        });
    }
}
