package org.telegram.messenger.plugins;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
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

    public static final class DebugResult {
        public final String operation;
        public final String pluginId;
        public final String version;
        public final String runtimeState;
        public final long generation;
        public final PluginUnloadReport unloadReport;

        DebugResult(String operation, String pluginId, String version,
                String runtimeState, long generation, PluginUnloadReport unloadReport) {
            this.operation = operation;
            this.pluginId = pluginId;
            this.version = version;
            this.runtimeState = runtimeState;
            this.generation = generation;
            this.unloadReport = unloadReport;
        }

        public String toJson(Exception error) {
            try {
                JSONObject result = new JSONObject();
                result.put("success", error == null);
                result.put("operation", operation);
                result.put("plugin_id", pluginId == null ? JSONObject.NULL : pluginId);
                result.put("version", version == null ? JSONObject.NULL : version);
                result.put("runtime_state",
                        runtimeState == null ? JSONObject.NULL : runtimeState);
                result.put("generation", generation);

                if (unloadReport != null) {
                    result.put("unload_report", new JSONObject(unloadReport.toJson()));
                }

                if (error != null) {
                    result.put("error", error.getClass().getSimpleName());
                    result.put("message", boundedMessage(error));
                }

                return result.toString();
            } catch (JSONException jsonError) {
                throw new IllegalStateException("Cannot encode plugin debug result", jsonError);
            }
        }

        private static String boundedMessage(Exception error) {
            String message = error.getMessage();

            if (message == null || message.isEmpty()) {
                message = error.getClass().getSimpleName();
            }

            return message.length() <= 240 ? message : message.substring(0, 240);
        }
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
                        PluginManager manager = PluginManager.getInstance();

                        if (manager.isRecoveryBlockingImports()) {
                            pending = Collections.unmodifiableMap(operations);
                            loadMetadataOnly(manager);
                            changed();

                            return;
                        }

                        for (PluginPackageStore.Pending operation : operations.values()) {
                            if (operation.delete) {
                                PluginStorage.clearPlugin(operation.id);

                                if (!PluginSettings.clearForPluginNow(operation.id)
                                        || !manager.clearPersistedEnabledStateNow(operation.id)) {
                                    throw new IOException("Cannot clear deleted plugin state");
                                }

                                store.applyPendingDelete(operation.id);
                                continue;
                            }

                            if (operation.rollback) {
                                store.rollbackPending(operation.id);

                                if (!manager.setPersistedEnabledState(
                                        operation.id,
                                        operation.enabledBeforeUpdate)) {
                                    throw new IOException("Cannot restore plugin enabled state");
                                }

                                store.finishPendingRollback(operation.id);

                                continue;
                            }

                            if (operation.migrated) {
                                if (!manager.setPersistedEnabledState(
                                        operation.id,
                                        operation.enableAfterUpdate)) {
                                    throw new IOException("Cannot save plugin enabled state");
                                }

                                store.finishPendingUpdate(operation.id);
                                continue;
                            }

                            PluginPackageStore.Installed update =
                                    store.activatePendingUpdate(operation.id);

                            if (!update.manifest.isCompatible()) {
                                store.rollbackPending(operation.id);

                                if (!manager.setPersistedEnabledState(
                                        operation.id,
                                        operation.enabledBeforeUpdate)) {
                                    throw new IOException("Cannot restore plugin enabled state");
                                }

                                store.finishPendingRollback(operation.id);
                                continue;
                            }

                            try {
                                PluginCrashRecovery.runExternal(
                                        operation.id,
                                        () -> PythonPluginAdapter.migrateStorage(update));
                            } catch (Exception | LinkageError error) {
                                PluginCrashRecovery.handled(error);
                                FileLog.e(error);
                                store.rollbackPending(operation.id);

                                if (!manager.setPersistedEnabledState(
                                        operation.id,
                                        operation.enabledBeforeUpdate)) {
                                    throw new IOException("Cannot restore plugin enabled state");
                                }

                                store.finishPendingRollback(operation.id);

                                continue;
                            }

                            store.markPendingMigrated(operation.id);

                            if (!manager.setPersistedEnabledState(
                                    operation.id,
                                    operation.enableAfterUpdate)) {
                                throw new IOException("Cannot save plugin enabled state");
                            }

                            store.finishPendingUpdate(operation.id);
                        }

                        LinkedHashMap<String, IncompatiblePlugin> unavailable =
                                new LinkedHashMap<>();

                        for (PluginPackageStore.Installed installed : store.list()) {
                            if (installed.manifest.isCompatible()) {
                                PythonPluginAdapter adapter = new PythonPluginAdapter(installed);

                                try {
                                    PluginCrashRecovery.runExternal(
                                            installed.manifest.id,
                                            () -> PythonPluginAdapter.migrateStorage(installed));
                                } catch (Exception | LinkageError error) {
                                    PluginCrashRecovery.handled(error);
                                    FileLog.e(error);
                                    disableAfterMigrationFailure(
                                            manager,
                                            installed.manifest.id
                                    );
                                    adapter.setPreflightError(error);
                                }

                                manager.register(adapter, false);
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

                        if (manager.isRecoveryBlockingImports()) {
                            throw new IOException(
                                    "Restart FluxGram before installing or updating plugins");
                        }

                        if (expectedVersion != null) {
                            boolean enabledBefore = manager.isEnabled(id);
                            store.scheduleUpdate(
                                    review.archive,
                                    review.result.sha256,
                                    id,
                                    expectedVersion,
                                    enable,
                                    enabledBefore);
                            manager.blockUntilRestart(id);
                            pending = Collections.unmodifiableMap(store.pending());
                        } else {
                            String installedId = id;
                            PluginPackageStore.Installed installed =
                                    store.install(
                                            review.archive,
                                            review.result.sha256,
                                            () -> PluginStorage.clearPlugin(installedId));
                            PythonPluginAdapter adapter = new PythonPluginAdapter(installed);
                            boolean migrated = true;

                            try {
                                PluginCrashRecovery.runExternal(
                                        installed.manifest.id,
                                        () -> PythonPluginAdapter.migrateStorage(installed));
                            } catch (Exception | LinkageError error) {
                                PluginCrashRecovery.handled(error);
                                migrated = false;
                                disableAfterMigrationFailure(manager, id);
                                adapter.setPreflightError(error);
                            }

                            manager.register(adapter, false);

                            if (enable && migrated) {
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

    public static void debugInstallOrReload(File archive, String expectedHash,
            boolean reload, boolean enable, Callback<DebugResult> callback) {
        if (!BuildConfig.DEBUG) {
            callback.complete(null, new IOException("Plugin debug bridge is unavailable"));
            return;
        }

        worker.execute(() -> {
            DebugResult result = null;
            Exception failure = null;
            String id = null;
            String version = null;
            PluginUnloadReport unloadReport = null;

            try {
                if (store == null || startupError != null) {
                    throw new IOException("Plugin storage unavailable: " + startupError);
                }

                PluginManager manager = PluginManager.getInstance();

                if (manager.isRecoveryBlockingImports()) {
                    throw new IOException("Restart FluxGram before using plugin debug reload");
                }

                PluginPackageValidator.Result checked =
                        PluginPackageValidator.inspect(archive, archive.getParentFile());

                if (!checked.sha256.equals(expectedHash)) {
                    throw new IOException("Uploaded plugin hash does not match the request");
                }

                id = checked.manifest.id;
                version = checked.manifest.version;

                if (!reload) {
                    PluginPackageStore.Installed installed = store.install(archive, expectedHash);
                    PythonPluginAdapter adapter = new PythonPluginAdapter(installed);
                    Exception migrationFailure = migrateForDebug(installed);

                    if (migrationFailure != null) {
                        disableAfterMigrationFailure(manager, id);
                        adapter.setPreflightError(migrationFailure);
                    }

                    manager.register(adapter, false);

                    if (enable && migrationFailure == null) {
                        manager.setEnabled(id, true);
                    }

                    String runtimeState = adapter.awaitDebugRuntime(10_000);

                    if (migrationFailure != null) {
                        throw migrationFailure;
                    }

                    result = new DebugResult(
                            "install", id, version, runtimeState,
                            adapter.getGeneration(), null);
                } else {
                    Plugin plugin = manager.getPlugin(id);

                    if (!(plugin instanceof PythonPluginAdapter)
                            || !((PythonPluginAdapter) plugin).isExternal()) {
                        throw new IOException("Debug reload requires an installed external plugin");
                    }

                    PythonPluginAdapter previous = (PythonPluginAdapter) plugin;
                    boolean enabledBefore = manager.isEnabled(id);
                    store.scheduleDebugReload(
                            archive, expectedHash, id, previous.getVersion(), enable);
                    pending = Collections.unmodifiableMap(store.pending());

                    try {
                        previous.stopForRemoval();
                        unloadReport = previous.getLastUnloadReport();

                        if (unloadReport == null || !unloadReport.successful) {
                            throw new IOException("Plugin cleanup report is incomplete");
                        }
                    } catch (Exception cleanupError) {
                        try {
                            store.cancelPendingUpdate(id);
                            pending = Collections.unmodifiableMap(store.pending());
                        } catch (Exception cancelError) {
                            cleanupError.addSuppressed(cancelError);
                        }

                        throw cleanupError;
                    }

                    if (!manager.unregisterStopped(id, previous)) {
                        throw new IOException("Stopped plugin registration changed during reload");
                    }

                    PluginPackageStore.Installed replacement = store.activatePendingUpdate(id);

                    Exception migrationError = migrateForDebug(replacement);

                    if (migrationError != null) {
                        store.rollbackPending(id);

                        if (!manager.setPersistedEnabledState(id, enabledBefore)) {
                            throw new IOException(
                                    "Cannot restore plugin enabled state", migrationError);
                        }

                        store.finishPendingRollback(id);
                        pending = Collections.unmodifiableMap(store.pending());
                        PythonPluginAdapter restored = new PythonPluginAdapter(store.get(id));
                        manager.register(restored, false);
                        restored.awaitDebugRuntime(10_000);
                        throw new IOException("Debug reload migration failed", migrationError);
                    }

                    store.markPendingMigrated(id);

                    if (!manager.setPersistedEnabledState(id, enable)) {
                        throw new IOException("Cannot save plugin enabled state");
                    }

                    store.finishPendingUpdate(id);
                    pending = Collections.unmodifiableMap(store.pending());
                    PythonPluginAdapter adapter = new PythonPluginAdapter(store.get(id));
                    manager.register(adapter, false);
                    String runtimeState = adapter.awaitDebugRuntime(10_000);
                    result = new DebugResult(
                            "reload", id, version, runtimeState,
                            adapter.getGeneration(), unloadReport);
                }
            } catch (Exception | LinkageError error) {
                if (id != null) {
                    restoreDebugRegistration(id, error);
                }

                failure = error instanceof Exception
                        ? (Exception) error : new IOException("Plugin debug operation failed", error);
            } finally {
                archive.delete();
            }

            if (result == null) {
                result = new DebugResult(
                        reload ? "reload" : "install", id, version,
                        null, -1, unloadReport);
            }

            DebugResult value = result;
            Exception error = failure;
            AndroidUtilities.runOnUIThread(() -> {
                changed();
                callback.complete(value, error);
            });
        });
    }

    private static Exception migrateForDebug(PluginPackageStore.Installed installed) {
        try {
            PluginCrashRecovery.runExternal(
                    installed.manifest.id,
                    () -> PythonPluginAdapter.migrateStorage(installed));
            return null;
        } catch (Exception | Error error) {
            PluginCrashRecovery.handled(error);
            return error instanceof Exception
                    ? (Exception) error : new IOException("Plugin migration failed", error);
        }
    }

    private static void restoreDebugRegistration(String id, Throwable failure) {
        PluginManager manager = PluginManager.getInstance();

        if (manager.getPlugin(id) != null || store == null) {
            return;
        }

        try {
            PluginPackageStore.Installed installed = store.get(id);
            PythonPluginAdapter adapter = new PythonPluginAdapter(installed);
            manager.register(adapter, false);
            adapter.awaitDebugRuntime(10_000);
        } catch (Exception | LinkageError restoreError) {
            failure.addSuppressed(restoreError);
        }
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

    private static void disableAfterMigrationFailure(PluginManager manager, String id) {
        manager.clearPersistedEnabledState(id);
    }

    private static void loadMetadataOnly(PluginManager manager) throws IOException {
        LinkedHashMap<String, IncompatiblePlugin> unavailable = new LinkedHashMap<>();

        for (PluginPackageStore.Installed installed : store.list()) {
            if (installed.manifest.isCompatible()) {
                manager.register(new PythonPluginAdapter(installed), false);
            } else {
                unavailable.put(installed.manifest.id, new IncompatiblePlugin(installed));
            }
        }

        incompatible = Collections.unmodifiableMap(unavailable);
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

                        store.scheduleDelete(id);
                        pending = Collections.unmodifiableMap(store.pending());
                        PluginStorage.clearPlugin(id);

                        if (!PluginSettings.clearForPluginNow(id)
                                || !manager.clearPersistedEnabledStateNow(id)) {
                            throw new IOException("Cannot clear deleted plugin state");
                        }

                        store.applyPendingDelete(id);

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
