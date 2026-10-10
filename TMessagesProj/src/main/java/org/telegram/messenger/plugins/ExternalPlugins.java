package org.telegram.messenger.plugins;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.packages.PluginManifest;
import org.telegram.messenger.plugins.packages.PluginPackageStore;
import org.telegram.messenger.plugins.packages.PluginPackageValidator;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ExternalPlugins {
    private static final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "FluxGramInstaller"));
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final Set<String> removing = ConcurrentHashMap.newKeySet();
    private static PluginPackageStore store;
    private static boolean initialized;
    private static volatile String startupError;
    private static volatile Map<String, PluginPackageStore.Pending> pending =
            Collections.emptyMap();
    private static volatile Map<String, IncompatiblePlugin> incompatible =
            Collections.emptyMap();

    public static PluginPackageStore.Pending getPending(String id) {
        return pending.get(id);
    }

    public interface Callback<T> {
        void complete(T value, Exception error);
    }

    public static final class IncompatiblePlugin implements Plugin {
        private final PluginPackageStore.Installed installed;
        private final File iconFile;

        IncompatiblePlugin(PluginPackageStore.Installed installed) {
            this.installed = installed;

            File resolvedIcon = null;

            if (installed.manifest.icon != null) {
                File file = new File(installed.directory, installed.manifest.icon);

                try {
                    if (file.getCanonicalPath().startsWith(
                            installed.directory.getCanonicalPath() + File.separator)) {
                        resolvedIcon = file;
                    }
                } catch (IOException error) {
                    FileLog.e(error);
                }
            }

            iconFile = resolvedIcon;
        }

        public PluginManifest getManifest() {
            return installed.manifest;
        }

        @Override
        public String getId() {
            return installed.manifest.id;
        }

        @Override
        public String getName() {
            return installed.manifest.name;
        }

        @Override
        public String getDescription() {
            return installed.manifest.description;
        }

        @Override
        public String getVersion() {
            return installed.manifest.version;
        }

        @Override
        public File getIconFile() {
            return iconFile;
        }

        @Override
        public void beforeSend(BeforeSendEvent event) {}
    }

    public static final class Review implements AutoCloseable {
        public final Bitmap icon;
        public final File archive;
        public final PluginPackageValidator.Result result;

        Review(File archive, PluginPackageValidator.Result result) throws IOException {
            this.icon = result.iconBytes == null ? null
                    : PluginIcons.decode(result.iconBytes, result.manifest.icon);
            this.archive = archive;
            this.result = result;
        }

        @Override
        public void close() {
            archive.delete();
        }
    }

    public static synchronized void initialize(Context context) {
        if (initialized) {
            return;
        }

        initialized = true;

        Context app = context.getApplicationContext();

        worker.execute(
                () -> {
                    try {
                        store =
                                new PluginPackageStore(
                                        new File(app.getFilesDir(), "plugins/installed"),
                                        app.getCacheDir());

                        Map<String, PluginPackageStore.Pending> operations = store.pending();
                        android.content.SharedPreferences prefs =
                                app.getSharedPreferences("fluxgram_plugins", Context.MODE_PRIVATE);
                        android.content.SharedPreferences.Editor edit = prefs.edit();

                        for (PluginPackageStore.Pending operation : operations.values()) {
                            if (operation.delete) {
                                edit.remove("enabled." + operation.id);
                            } else {
                                edit.putBoolean(
                                        "enabled." + operation.id,
                                        operation.enableAfterUpdate);
                            }
                        }

                        if (!operations.isEmpty() && !edit.commit()) {
                            throw new IOException("Cannot save plugin states");
                        }

                        store.applyPending();

                        LinkedHashMap<String, IncompatiblePlugin> unavailable =
                                new LinkedHashMap<>();
                        PluginManager manager = PluginManager.getInstance();

                        for (PluginPackageStore.Installed installed : store.list()) {
                            if (installed.manifest.isCompatible()) {
                                manager.register(new PythonPluginAdapter(installed), false);
                            } else {
                                unavailable.put(
                                        installed.manifest.id,
                                        new IncompatiblePlugin(installed));
                                manager.clearPersistedEnabledState(installed.manifest.id);
                            }
                        }

                        incompatible = Collections.unmodifiableMap(unavailable);
                    } catch (Exception error) {
                        startupError = error.toString();
                        FileLog.e(error);
                    }

                    changed();
                });
    }

    public static String getStartupError() {
        return startupError;
    }

    public static ArrayList<Plugin> getIncompatiblePlugins() {
        return new ArrayList<>(incompatible.values());
    }

    public static Plugin getIncompatiblePlugin(String id) {
        return incompatible.get(id);
    }

    public static boolean isExternal(Plugin plugin) {
        return plugin instanceof IncompatiblePlugin
                || (plugin instanceof PythonPluginAdapter
                && ((PythonPluginAdapter) plugin).isExternal());
    }

    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public static void changed() {
        AndroidUtilities.runOnUIThread(
                () -> {
                    for (Runnable listener : listeners) {
                        listener.run();
                    }
                });
    }

    public static void review(Context context, Uri uri, Callback<Review> callback) {
        Context app = context.getApplicationContext();

        worker.execute(
                () -> {
                    File snapshot = null;
                    Review review = null;
                    Exception failure = null;

                    try {
                        snapshot =
                                File.createTempFile("fgplugin-picked-", ".zip", app.getCacheDir());

                        try (InputStream in = app.getContentResolver().openInputStream(uri);
                                OutputStream out = new FileOutputStream(snapshot)) {
                            if (in == null) {
                                throw new IOException("Cannot open selected document");
                            }

                            PluginPackageStore.copy(
                                    in, out, PluginPackageValidator.MAX_ARCHIVE_BYTES);
                        }

                        review =
                                new Review(
                                        snapshot,
                                        PluginPackageValidator.inspect(
                                                snapshot, app.getCacheDir()));
                    } catch (Exception error) {
                        failure = error;

                        if (snapshot != null) {
                            snapshot.delete();
                        }
                    }

                    Review value = review;
                    Exception error = failure;

                    AndroidUtilities.runOnUIThread(() -> callback.complete(value, error));
                });
    }

    public static void install(
            Review review, boolean enable, String expectedVersion, Callback<String> callback) {
        worker.execute(
                () -> {
                    String id = null;
                    Exception failure = null;

                    try {
                        if (store == null || startupError != null) {
                            throw new IOException("Plugin storage unavailable: " + startupError);
                        }

                        id = review.result.manifest.id;

                        PluginManager manager = PluginManager.getInstance();

                        if (expectedVersion != null) {
                            store.scheduleUpdate(
                                    review.archive,
                                    review.result.sha256,
                                    id,
                                    expectedVersion,
                                    enable);
                            manager.blockUntilRestart(id);
                            pending = Collections.unmodifiableMap(store.pending());
                        } else {
                            PluginPackageStore.Installed installed =
                                    store.install(review.archive, review.result.sha256);
                            manager.register(new PythonPluginAdapter(installed), false);

                            if (enable) {
                                manager.setEnabled(id, true);
                            }
                        }
                    } catch (Exception error) {
                        failure = error;
                    } finally {
                        review.close();
                    }

                    String value = id;
                    Exception error = failure;

                    AndroidUtilities.runOnUIThread(
                            () -> {
                                changed();
                                callback.complete(value, error);
                            });
                });
    }

    public static void export(Context context, String id, Callback<File> callback) {
        File cacheDirectory = context.getExternalCacheDir();

        worker.execute(() -> {
            File result = null;
            Exception failure = null;

            try {
                if (store == null || startupError != null) {
                    throw new IOException("Plugin storage unavailable: " + startupError);
                }

                if (cacheDirectory == null) {
                    throw new IOException("Shared file storage is unavailable");
                }

                result = store.export(id, new File(cacheDirectory, "plugin-shares"));
            } catch (Exception error) {
                failure = error;
            }

            File file = result;
            Exception error = failure;
            AndroidUtilities.runOnUIThread(() -> callback.complete(file, error));
        });
    }

    public static boolean isRemoving(String id) {
        return removing.contains(id);
    }

    public static void delete(String id, Callback<String> callback) {
        if (!removing.add(id)) {
            AndroidUtilities.runOnUIThread(() -> callback.complete(
                    id, new IOException("Plugin removal is already in progress")));
            return;
        }

        changed();
        worker.execute(
                () -> {
                    Exception failure = null;

                    try {
                        if (store == null || startupError != null) {
                            throw new IOException("Plugin storage unavailable: " + startupError);
                        }

                        PluginManager manager = PluginManager.getInstance();
                        Plugin plugin = manager.getPlugin(id);
                        IncompatiblePlugin incompatiblePlugin = incompatible.get(id);
                        boolean runningExternal = plugin instanceof PythonPluginAdapter
                                && ((PythonPluginAdapter) plugin).isExternal();

                        if (!runningExternal && incompatiblePlugin == null) {
                            throw new IOException("Only installed external plugins can be removed");
                        }

                        if (getPending(id) != null) {
                            throw new IOException("Plugin already has a pending operation");
                        }

                        if (runningExternal) {
                            manager.blockUntilRestart(id);
                            changed();
                            ((PythonPluginAdapter) plugin).stopForRemoval();

                            CountDownLatch uiCleanup = new CountDownLatch(1);
                            AndroidUtilities.runOnUIThread(uiCleanup::countDown);

                            if (!uiCleanup.await(10, TimeUnit.SECONDS)) {
                                throw new IOException("Plugin UI cleanup has not finished. Try deleting it again shortly.");
                            }
                        }

                        store.remove(id);
                        PluginSettings.clearForPlugin(id);
                        manager.clearPersistedEnabledState(id);

                        if (runningExternal) {
                            manager.unregister(id);
                        } else {
                            LinkedHashMap<String, IncompatiblePlugin> remaining =
                                    new LinkedHashMap<>(incompatible);
                            remaining.remove(id);
                            incompatible = Collections.unmodifiableMap(remaining);
                        }

                        pending = Collections.unmodifiableMap(store.pending());
                    } catch (Exception error) {
                        failure = error;
                    }

                    removing.remove(id);
                    Exception error = failure;

                    AndroidUtilities.runOnUIThread(
                            () -> {
                                changed();
                                callback.complete(id, error);
                            });
                });
    }
}
