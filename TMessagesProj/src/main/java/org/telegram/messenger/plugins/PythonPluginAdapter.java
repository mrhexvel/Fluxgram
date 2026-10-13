package org.telegram.messenger.plugins;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import com.chaquo.python.PyObject;

import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.plugins.packages.PluginManifest;
import org.telegram.messenger.plugins.api.JavaPluginUiResource;
import org.telegram.messenger.plugins.api.ComposerContentEffect;
import org.telegram.messenger.plugins.api.ComposerCursorEffect;
import org.telegram.messenger.plugins.api.ComposerSelectionEffect;
import org.telegram.messenger.plugins.api.ComposerEffect;
import org.telegram.messenger.plugins.api.ComposerTextEffect;
import org.telegram.messenger.plugins.packages.PluginPackageStore;

import java.io.File;
import java.io.IOException;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class PythonPluginAdapter implements Plugin {
    private static final String TAG = "FluxGramPython";
    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
    private static final int MAILBOX_CAPACITY = 69;
    private final String id;
    private final String moduleName;
    private final int nameResId;
    private final int descriptionResId;
    private final int iconResId;
    private final String version;
    private volatile String externalPath;
    private String externalName, externalDescription;
    private PluginManifest.JavaModule javaModule;
    private JavaPluginLoader javaLoader;
    private JavaPluginUiStop javaUiStop;
    private PluginSession pluginSession;
    private volatile Thread workerThread;
    private volatile String loadError;
    private File iconFile;
    private volatile java.util.List<PluginSettings.Setting> settingsSchema;

    public PythonPluginAdapter(
            PluginPackageStore.Installed installed) {
        this(
                installed.manifest.id,
                installed.manifest.entrypoint.split(":")[0],
                0,
                0,
                0,
                installed.manifest.version);
        externalPath = installed.directory.getAbsolutePath();
        javaModule = installed.manifest.javaModule;
        externalName = installed.manifest.name;
        externalDescription = installed.manifest.description;

        if (installed.manifest.icon != null) {
            File file = new File(installed.directory, installed.manifest.icon);

            try {
                if (file.getCanonicalPath().startsWith(installed.directory.getCanonicalPath() + File.separator)) {
                    iconFile = file;
                }
            } catch (IOException error) {
                FileLog.e(error);
            }
        }
    }

    @Override
    public File getIconFile() {
        return iconFile;
    }

    @Override
    public int getIconResource() {
        return iconResId;
    }

    public boolean isExternal() {
        return externalPath != null;
    }

    public String getLoadError() {
        return loadError;
    }

    public String getRuntimeState() {
        return lifecycle.getState().name();
    }

    public boolean hasJavaModule() {
        return javaModule != null;
    }

    void setPreflightError(Throwable error) {
        loadError = error.toString();
    }

    public static void migrateStorage(
            PluginPackageStore.Installed installed) {
        migrateExternalStorage(
                installed.directory.getAbsolutePath(),
                installed.manifest.entrypoint.split(":")[0],
                installed.manifest.id
        );
    }

    private static void migrateExternalStorage(String path, String module, String pluginId) {
        PythonRuntime.prepare()
                .getModule("fluxgram_runtime._bridge")
                .callAttr("migrate_external_plugin", path, module, pluginId);
    }

    private final ThreadPoolExecutor executor;
    private final Semaphore eventSlots = new Semaphore(64);
    private final Semaphore storageSlots = new Semaphore(2);
    private final PythonPluginLifecycle lifecycle = new PythonPluginLifecycle();
    private final PythonPluginTaskScheduler taskScheduler;
    private final LinkedHashMap<String, PendingSetting> pendingSettings = new LinkedHashMap<>();
    private final LinkedHashMap<String, Boolean> observedMessages = new LinkedHashMap<>();
    private boolean settingsDrainQueued;
    private PyObject bridge;
    private PyObject instance;
    private FutureTask<Void> removal;
    private volatile boolean messageReceivedSubscribers;
    private volatile boolean messageEditedSubscribers;
    private volatile boolean messageDeletedSubscribers;
    private volatile boolean chatOpenedSubscribers;
    private volatile boolean chatClosedSubscribers;
    private volatile boolean applicationForegroundedSubscribers;
    private volatile boolean applicationBackgroundedSubscribers;

    private static final class PendingSetting {
        final long generation;
        final int account;
        final long ownerId;
        final Object value;

        PendingSetting(long generation, int account, long ownerId, Object value) {
            this.generation = generation;
            this.account = account;
            this.ownerId = ownerId;
            this.value = value;
        }
    }

    public PythonPluginAdapter(
            String id, String moduleName, int nameResId, int descriptionResId, int iconResId, String version) {
        this.id = id;
        this.moduleName = moduleName;
        this.nameResId = nameResId;
        this.descriptionResId = descriptionResId;
        this.iconResId = iconResId;
        this.version = version;
        settingsSchema = PluginSettings.readSchema(id, version);

        executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        30L,
                        TimeUnit.SECONDS,
                        new PythonPluginTaskQueue(MAILBOX_CAPACITY),
                        runnable -> new Thread(() -> {
                            workerThread = Thread.currentThread();

                            if (isExternal()) {
                                PluginCrashRecovery.runExternalWorker(id, runnable);
                            } else {
                                runnable.run();
                            }
                        }, "FluxGramPlugin-" + id));

        executor.allowCoreThreadTimeOut(true);
        taskScheduler = new PythonPluginTaskScheduler(
                id,
                new PythonPluginTaskScheduler.Host() {
                    @Override
                    public boolean isGenerationActive(long generation) {
                        return lifecycle.isCurrent(generation);
                    }

                    @Override
                    public boolean enqueue(Runnable task) {
                        return enqueueManagedTask(task);
                    }
                }
        );
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return isExternal() ? externalName : LocaleController.getString(nameResId);
    }

    @Override
    public String getDescription() {
        return isExternal() ? externalDescription : LocaleController.getString(descriptionResId);
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public synchronized void onEnabledChanged(boolean enabled) {
        if (lifecycle.isRemovalPending()) {
            return;
        }

        if (enabled) {
            loadError = null;
        }

        boolean reconcile = lifecycle.requestEnabled(enabled);
        taskScheduler.cancelAll();
        observedMessages.clear();
        messageReceivedSubscribers = false;
        messageEditedSubscribers = false;
        messageDeletedSubscribers = false;
        chatOpenedSubscribers = false;
        chatClosedSubscribers = false;
        applicationForegroundedSubscribers = false;
        applicationBackgroundedSubscribers = false;

        if (reconcile) {
            try {
                executeControl(this::reconcile);
            } catch (RejectedExecutionException error) {
                lifecycle.cancelReconcile();
                Log.w(TAG, "[" + id + "] Reconcile rejected", error);
            }
        }
    }

    private void executeControl(Runnable task) {
        executor.execute(PythonPluginTaskQueue.control(task));
    }

    private void reconcile() {
        while (true) {
            PythonPluginLifecycle.Request request = lifecycle.currentRequest();

            if (request.removal) {
                lifecycle.finishReconcile(request.generation);

                return;
            }

            if (instance != null || javaLoader != null || javaUiStop != null || pluginSession != null) {
                lifecycle.setState(request.generation, PythonPluginLifecycle.State.STOPPING);

                if (javaUiStop != null && !javaUiStop.prepare(request.generation, this::reconcile)) {
                    return;
                }

                if (!unload(request.generation, request.enabled)) {
                    if (lifecycle.finishReconcile(request.generation)) {
                        continue;
                    }

                    return;
                }
            }

            if (!request.enabled || !lifecycle.isCurrent(request.generation)) {
                lifecycle.setState(
                        request.generation,
                        loadError == null
                                ? PythonPluginLifecycle.State.DISABLED
                                : PythonPluginLifecycle.State.FAILED);
            } else {
                load(request.generation);

                if ((instance != null || javaLoader != null || javaUiStop != null || pluginSession != null)
                        && !lifecycle.isCurrent(request.generation)
                        && !lifecycle.isRemovalPending()) {
                    continue;
                }
            }

            if (!lifecycle.finishReconcile(request.generation)) {
                return;
            }
        }
    }

    private void load(long request) {
        if (!lifecycle.setState(request, PythonPluginLifecycle.State.STARTING)) {
            return;
        }

        try {
            PyObject nextBridge = PythonRuntime.prepare().getModule("fluxgram_runtime._bridge");

            if (!lifecycle.isCurrent(request)) {
                return;
            }

            if (isExternal()) {
                nextBridge.callAttr(
                        "migrate_external_plugin",
                        externalPath,
                        moduleName,
                        id
                );
            } else {
                nextBridge.callAttr("migrate_plugin", moduleName, id);
            }

            loadError = null;

            if (!lifecycle.isCurrent(request)) {
                return;
            }

            pluginSession = new PluginSession(
                    id,
                    request,
                    () -> lifecycle.isCurrent(request),
                    () -> {
                        if (Thread.currentThread() != workerThread) {
                            throw new IllegalStateException("Plugin session requires the plugin worker");
                        }
                    });

            if (javaModule != null) {
                PluginScope uiScope = pluginSession.openExternalScope("ui");
                JavaPluginUiResources uiResources = new JavaPluginUiResources(
                        new JavaPluginUiResources.Dispatcher() {
                            @Override
                            public void post(Runnable action) {
                                UI_HANDLER.post(action);
                            }

                            @Override
                            public boolean isUiThread() {
                                return Looper.myLooper() == Looper.getMainLooper();
                            }
                        },
                        uiScope,
                        error -> Log.e(TAG, "[" + id + "] Java UI resource failed", error));
                javaUiStop = new JavaPluginUiStop(uiResources, this::executeControl);
                javaLoader = JavaPluginLoader.prepare(
                        ApplicationLoader.applicationContext, new File(externalPath), javaModule,
                        pluginSession, javaHost(uiResources));

                if (!lifecycle.isCurrent(request)) {
                    return;
                }
            }

            PyObject nextInstance =
                    isExternal()
                            ? nextBridge.callAttr(
                                    "create_external_plugin",
                                    externalPath,
                                    moduleName,
                                    id,
                                    this,
                                    request)
                            : nextBridge.callAttr("create_plugin", moduleName, id, this, request);

            bridge = nextBridge;
            instance = nextInstance;

            if (!lifecycle.isCurrent(request)) {
                return;
            }

            String schema = bridge.callAttr("describe_settings", instance).toString();
            java.util.List<PluginSettings.Setting> parsed = PluginSettings.parse(schema);

            if (!lifecycle.isCurrent(request)) {
                return;
            }

            settingsSchema = parsed;
            PluginSettings.saveSchema(id, version, schema);
            ExternalPlugins.changed();

            if (!lifecycle.isCurrent(request)) {
                return;
            }

            if (javaLoader != null) {
                javaLoader.start();

                if (!lifecycle.isCurrent(request)) {
                    return;
                }
            }

            instance.callAttr("on_load");

            if (!lifecycle.isCurrent(request)) {
                return;
            }

            if (lifecycle.setState(request, PythonPluginLifecycle.State.RUNNING) && javaUiStop != null) {
                javaUiStop.activate();
            }
        } catch (Exception | LinkageError error) {
            handleFailure(request, "Load", error, true);
        }
    }

    private void handleFailure(
            long request,
            String operation,
            Throwable error,
            boolean disableSelection) {
        String message = error.toString();
        Log.e(TAG, "[" + id + "] " + operation + " failed", error);

        if (!lifecycle.fail(request)) {
            return;
        }

        taskScheduler.cancelGeneration(request);

        loadError = message;
        org.telegram.messenger.AndroidUtilities.runOnUIThread(
                () -> {
                    if (lifecycle.getGeneration() != request
                            || lifecycle.isEnabled()
                            || lifecycle.isRemovalPending()) {
                        return;
                    }

                    if (disableSelection) {
                        PluginManager.getInstance().setEnabled(id, false);
                    }

                    loadError = message;
                    ExternalPlugins.changed();
                });
    }

    private JavaPluginSession.Host javaHost(JavaPluginUiResources uiResources) {
        return new JavaPluginSession.Host() {
            @Override
            public boolean isActive(long generation) {
                return lifecycle.isCurrent(generation);
            }

            @Override
            public boolean isWorkerThread() {
                return Thread.currentThread() == workerThread;
            }

            @Override
            public boolean readBooleanSetting(String key, boolean fallback) {
                return PythonPluginAdapter.this.readBooleanSetting(key, fallback);
            }

            @Override
            public int readIntSetting(String key, int fallback) {
                return PythonPluginAdapter.this.readIntSetting(key, fallback);
            }

            @Override
            public String readStringSetting(String key, String fallback) {
                return PythonPluginAdapter.this.readStringSetting(key, fallback);
            }

            @Override
            public void log(String message) {
                Log.i(TAG, "[" + id + "] Java: " + message);
            }

            @Override
            public JavaPluginUiResource registerUiResource(long generation, JavaPluginUiResource.Factory factory) {
                if (!lifecycle.isCurrent(generation)) {
                    throw new IllegalStateException("Java UI generation is inactive");
                }

                return uiResources.register(factory);
            }

            @Override
            public JavaPluginUiResource registerComposerEffect(long generation, ComposerEffect.Factory factory) {
                if (!lifecycle.isCurrent(generation)) {
                    throw new IllegalStateException("Composer generation is inactive");
                }

                return uiResources.register(() -> PluginComposerRegistry.register(
                        id, () -> lifecycle.isCurrent(generation), factory));
            }

            @Override
            public JavaPluginUiResource registerComposerTextEffect(long generation, ComposerTextEffect.Factory factory) {
                if (!lifecycle.isCurrent(generation)) {
                    throw new IllegalStateException("Composer text generation is inactive");
                }

                return uiResources.register(() -> PluginComposerRegistry.registerText(
                        id, () -> lifecycle.isCurrent(generation), factory));
            }

            @Override
            public JavaPluginUiResource registerComposerCursorEffect(
                    long generation, ComposerCursorEffect.Factory factory) {
                if (!lifecycle.isCurrent(generation)) {
                    throw new IllegalStateException("Composer cursor generation is inactive");
                }

                return uiResources.register(() -> PluginComposerRegistry.registerCursor(
                        id, () -> lifecycle.isCurrent(generation), factory));
            }

            @Override
            public JavaPluginUiResource registerComposerSelectionEffect(
                    long generation, ComposerSelectionEffect.Factory factory) {
                if (!lifecycle.isCurrent(generation)) {
                    throw new IllegalStateException("Composer selection generation is inactive");
                }

                return uiResources.register(() -> PluginComposerRegistry.registerSelection(
                        id, () -> lifecycle.isCurrent(generation), factory));
            }

            @Override
            public JavaPluginUiResource registerComposerContentEffect(
                    long generation, ComposerContentEffect.Factory factory) {
                if (!lifecycle.isCurrent(generation)) {
                    throw new IllegalStateException("Composer content generation is inactive");
                }

                return uiResources.register(() -> PluginComposerRegistry.registerContent(
                        id, () -> lifecycle.isCurrent(generation), factory));
            }
        };
    }

    public java.util.List<PluginSettings.Setting> getSettingsSchema() {
        return settingsSchema;
    }

    public boolean readBooleanSetting(String key, boolean fallback) {
        return PluginSettings.getBoolean(id, key, fallback);
    }

    public String readStringSetting(String key, String fallback) {
        return PluginSettings.getString(id, key, fallback);
    }

    public int readIntSetting(String key, int fallback) {
        return PluginSettings.getInt(id, key, fallback);
    }

    public void changeBooleanSetting(String key, boolean value) {
        changeSettingValue(key, value);
    }

    public synchronized void changeSettingValue(String key, Object value) {
        if (removal != null) {
            return;
        }

        PluginSettings.Setting declared = null;

        for (PluginSettings.Setting setting : settingsSchema) {
            if (setting.key.equals(key)) {
                declared = setting;
                break;
            }
        }

        if (declared == null) {
            throw new IllegalArgumentException("Unknown setting: " + key);
        }

        PluginSettings.setValue(id, declared, value);
        long request = lifecycle.getGeneration();
        int account = UserConfig.selectedAccount;
        long ownerId = UserConfig.getInstance(account).getClientUserId();

        if (lifecycle.isEnabled()) {
            pendingSettings.put(
                    key,
                    new PendingSetting(request, account, ownerId, value)
            );

            if (!settingsDrainQueued) {
                settingsDrainQueued = true;

                try {
                    executor.execute(this::drainSettings);
                } catch (RejectedExecutionException error) {
                    settingsDrainQueued = false;
                    pendingSettings.clear();
                    Log.w(TAG, "[" + id + "] Setting callback rejected", error);
                }
            }
        }
    }

    private void drainSettings() {
        long previewGeneration = -1;
        int previewAccount = -1;
        long previewOwnerId = 0;

        while (true) {
            String key;
            PendingSetting pending;

            synchronized (this) {
                Iterator<Map.Entry<String, PendingSetting>> iterator =
                        pendingSettings.entrySet().iterator();

                if (!iterator.hasNext()) {
                    settingsDrainQueued = false;
                    break;
                }

                Map.Entry<String, PendingSetting> entry = iterator.next();
                key = entry.getKey();
                pending = entry.getValue();
                iterator.remove();
            }

            if (!lifecycle.isCurrent(pending.generation) || instance == null) {
                continue;
            }

            if (UserConfig.getInstance(pending.account).getClientUserId() != pending.ownerId) {
                continue;
            }

            try {
                if (javaLoader != null) {
                    javaLoader.settingChanged(key, pending.value);
                }
            } catch (Exception | LinkageError error) {
                Log.e(TAG, "[" + id + "] Java setting callback failed", error);
            }

            if (!lifecycle.isCurrent(pending.generation)
                    || UserConfig.getInstance(pending.account).getClientUserId() != pending.ownerId) {
                continue;
            }

            try {
                bridge.callAttr(
                        "dispatch_setting_changed",
                        instance,
                        key,
                        pending.value,
                        pending.account,
                        pending.ownerId,
                        pending.generation
                );
            } catch (Exception | LinkageError error) {
                Log.e(TAG, "[" + id + "] Setting callback failed", error);
            }

            if (lifecycle.isCurrent(pending.generation)
                    && UserConfig.getInstance(pending.account).getClientUserId() == pending.ownerId) {
                previewGeneration = pending.generation;
                previewAccount = pending.account;
                previewOwnerId = pending.ownerId;
            }
        }

        if (previewGeneration >= 0) {
            long generation = previewGeneration;
            int account = previewAccount;
            long ownerId = previewOwnerId;
            UI_HANDLER.post(() -> {
                if (lifecycle.isCurrent(generation)
                        && UserConfig.getInstance(account).getClientUserId() == ownerId) {
                    PluginComposerRegistry.refreshPreview(id);
                }
            });
        }
    }

    public synchronized boolean isStoppingForRemoval() {
        return removal != null;
    }

    public void stopForRemoval() throws IOException {
        FutureTask<Void> task;

        synchronized (this) {
            if (removal == null || (removal.isDone() && !executor.isShutdown())) {
                lifecycle.beginRemoval();
                taskScheduler.cancelAll();
                pendingSettings.clear();
                removal = new FutureTask<>(() -> {
                    try {
                        unloadStrict();
                        executor.getQueue().clear();
                        lifecycle.terminate();
                        executor.shutdown();
                    } catch (Exception | LinkageError error) {
                        lifecycle.failRemoval();
                        throw error;
                    }

                    return null;
                });

                try {
                    scheduleRemoval(removal);
                } catch (RejectedExecutionException error) {
                    removal = null;
                    lifecycle.failRemoval();

                    throw new IOException("Plugin removal could not be scheduled", error);
                }
            }

            task = removal;
        }

        try {
            task.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Plugin removal interrupted", error);
        } catch (ExecutionException error) {
            throw new IOException("Plugin cleanup failed. Its files were kept. Retry removal after resolving the error.", error.getCause());
        } catch (TimeoutException error) {
            throw new IOException("Plugin has not finished stopping. Try deleting it again shortly.", error);
        }
    }

    public boolean isGenerationActive(long value) {
        return lifecycle.isCurrent(value);
    }

    private void scheduleRemoval(FutureTask<Void> task) {
        executeControl(() -> {
            if (javaUiStop == null
                    || javaUiStop.prepare(task, () -> scheduleRemoval(task))) {
                task.run();
            }
        });
    }

    public int submitStorage(long generation, PyObject callback) {
        if (!lifecycle.isCurrent(generation)) {
            return 2;
        }

        if (!storageSlots.tryAcquire()) {
            return 1;
        }

        try {
            executor.execute(
                    () -> {
                        try {
                            if (lifecycle.isCurrent(generation)) {
                                callback.call();
                            }
                        } catch (Exception | LinkageError error) {
                            Log.e(TAG, "[" + id + "] Storage task failed", error);
                        } finally {
                            storageSlots.release();
                        }
                    });
            return 0;
        } catch (RejectedExecutionException error) {
            storageSlots.release();
            return lifecycle.isCurrent(generation) ? 1 : 2;
        }
    }

    public int scheduleManagedTask(
            long generation,
            long taskId,
            long delayMs,
            long intervalMs,
            PyObject callback,
            PyObject completion) {
        return taskScheduler.schedule(
                generation,
                taskId,
                delayMs,
                intervalMs,
                callback,
                completion
        );
    }

    public void cancelManagedTask(long generation, long taskId) {
        taskScheduler.cancel(generation, taskId);
    }

    public void cancelManagedTasks(long generation) {
        taskScheduler.cancelGeneration(generation);
    }

    public boolean isManagedTaskActive(long generation, long taskId) {
        return taskScheduler.isActive(generation, taskId);
    }

    private boolean enqueueManagedTask(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException error) {
            return false;
        }
    }

    private boolean unload(long request, boolean disableSelection) {
        try {
            unloadStrict();
            return true;
        } catch (Exception | LinkageError error) {
            handleFailure(request, "Unload", error, disableSelection);

            return false;
        }
    }

    private void unloadStrict() throws Exception {
        Exception failure = null;

        if (javaUiStop != null) {
            try {
                javaUiStop.requireSuccess();
                javaUiStop = null;
            } catch (Exception error) {
                failure = error;
            }
        }

        if (javaLoader != null && javaUiStop == null) {
            try {
                javaLoader.close();
                javaLoader = null;
            } catch (Exception | LinkageError error) {
                failure = new Exception("Java cleanup failed", error);
            }
        }

        if (instance != null) {
            try {
                bridge.callAttr("unload_plugin", instance);
                instance = null;
                bridge = null;
            } catch (Exception | LinkageError error) {
                if (failure == null) {
                    failure = new Exception("Python cleanup failed", error);
                } else {
                    failure.addSuppressed(error);
                }
            }
        }

        if (pluginSession != null && javaUiStop == null && javaLoader == null && instance == null) {
            try {
                pluginSession.close();
                pluginSession = null;
            } catch (Exception | LinkageError error) {
                if (failure == null) {
                    failure = new Exception("Plugin session cleanup failed", error);
                } else {
                    failure.addSuppressed(error);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public synchronized void beforeSend(BeforeSendEvent event) {
        if (!lifecycle.isEnabled()) {
            return;
        }

        if (!eventSlots.tryAcquire()) {
            Log.w(TAG, "[" + id + "] Queue full; event skipped");

            return;
        }

        final long request = lifecycle.getGeneration();
        final int account = event.account;
        final int textLength = event.text.length();
        final int scheduleDate = event.scheduleDate;

        try {
            executor.execute(
                    () -> {
                        try {
                            if (!lifecycle.isCurrent(request) || instance == null) {
                                return;
                            }

                            JSONObject payload = new JSONObject();
                            payload.put("account", account);
                            payload.put("text_length", textLength);
                            payload.put("schedule_date", scheduleDate);

                            bridge.callAttr("dispatch_before_send", instance, payload.toString());
                        } catch (Exception | LinkageError error) {
                            Log.e(TAG, "[" + id + "] Handler failed", error);
                        } finally {
                            eventSlots.release();
                        }
                    });
        } catch (RejectedExecutionException error) {
            eventSlots.release();
            Log.w(TAG, "[" + id + "] Event rejected", error);
        }
    }

    @Override
    public synchronized void messageReceived(PluginMessageReceivedEvent event) {
        if (!lifecycle.isEnabled() || !messageReceivedSubscribers) {
            return;
        }

        String key = event.account
                + ":" + event.ownerId
                + ":" + event.dialogId
                + ":" + event.messageId;

        if (observedMessages.containsKey(key)) {
            return;
        }

        if (!eventSlots.tryAcquire()) {
            Log.w(TAG, "[" + id + "] Queue full; message event skipped");

            return;
        }

        observedMessages.put(key, true);

        if (observedMessages.size() > 512) {
            Iterator<String> iterator = observedMessages.keySet().iterator();
            iterator.next();
            iterator.remove();
        }

        final long request = lifecycle.getGeneration();
        final long ownerId = event.ownerId;

        try {
            executor.execute(
                    () -> {
                        try {
                            if (!lifecycle.isCurrent(request) || instance == null) {
                                return;
                            }

                            if (UserConfig.getInstance(event.account).getClientUserId() != ownerId) {
                                return;
                            }

                            bridge.callAttr(
                                    "dispatch_message_received",
                                    instance,
                                    event.toJson(),
                                    event.account,
                                    ownerId,
                                    request
                            );
                        } catch (Exception | LinkageError error) {
                            Log.e(TAG, "[" + id + "] Message event handler failed", error);
                        } finally {
                            eventSlots.release();
                        }
                    });
        } catch (RejectedExecutionException error) {
            eventSlots.release();
            observedMessages.remove(key);
            Log.w(TAG, "[" + id + "] Message event rejected", error);
        }
    }

    public synchronized void setMessageReceivedSubscriptionActive(
            long generation,
            boolean active) {
        if (lifecycle.isCurrent(generation)) {
            messageReceivedSubscribers = active;
        }
    }

    @Override
    public boolean hasMessageReceivedSubscribers() {
        return messageReceivedSubscribers;
    }

    @Override
    public synchronized void messageEdited(PluginMessageEditedEvent event) {
        if (!lifecycle.isEnabled() || !messageEditedSubscribers) {
            return;
        }

        if (!eventSlots.tryAcquire()) {
            Log.w(TAG, "[" + id + "] Queue full; message edited event skipped");

            return;
        }

        final long request = lifecycle.getGeneration();
        final long ownerId = event.ownerId;

        try {
            executor.execute(
                    () -> {
                        try {
                            if (!lifecycle.isCurrent(request) || instance == null) {
                                return;
                            }

                            if (UserConfig.getInstance(event.account).getClientUserId() != ownerId) {
                                return;
                            }

                            bridge.callAttr(
                                    "dispatch_message_edited",
                                    instance,
                                    event.toJson(),
                                    event.account,
                                    ownerId,
                                    request
                            );
                        } catch (Exception | LinkageError error) {
                            Log.e(TAG, "[" + id + "] Message edited event handler failed", error);
                        } finally {
                            eventSlots.release();
                        }
                    });
        } catch (RejectedExecutionException error) {
            eventSlots.release();
            Log.w(TAG, "[" + id + "] Message edited event rejected", error);
        }
    }

    public synchronized void setMessageEditedSubscriptionActive(
            long generation,
            boolean active) {
        if (lifecycle.isCurrent(generation)) {
            messageEditedSubscribers = active;
        }
    }

    @Override
    public boolean hasMessageEditedSubscribers() {
        return messageEditedSubscribers;
    }

    @Override
    public synchronized void messagesDeleted(PluginMessagesDeletedEvent event) {
        if (!lifecycle.isEnabled() || !messageDeletedSubscribers) {
            return;
        }

        if (!eventSlots.tryAcquire()) {
            Log.w(TAG, "[" + id + "] Queue full; message deleted batch skipped");

            return;
        }

        final long request = lifecycle.getGeneration();
        final long ownerId = event.ownerId;

        try {
            executor.execute(
                    () -> {
                        try {
                            if (!lifecycle.isCurrent(request) || instance == null) {
                                return;
                            }

                            if (UserConfig.getInstance(event.account).getClientUserId() != ownerId) {
                                return;
                            }

                            bridge.callAttr(
                                    "dispatch_messages_deleted",
                                    instance,
                                    event.toJson(),
                                    event.account,
                                    ownerId,
                                    request
                            );
                        } catch (Exception | LinkageError error) {
                            Log.e(TAG, "[" + id + "] Message deleted event handler failed", error);
                        } finally {
                            eventSlots.release();
                        }
                    });
        } catch (RejectedExecutionException error) {
            eventSlots.release();
            Log.w(TAG, "[" + id + "] Message deleted batch rejected", error);
        }
    }

    public synchronized void setMessageDeletedSubscriptionActive(
            long generation,
            boolean active) {
        if (lifecycle.isCurrent(generation)) {
            messageDeletedSubscribers = active;
        }
    }

    @Override
    public boolean hasMessageDeletedSubscribers() {
        return messageDeletedSubscribers;
    }

    @Override
    public void chatOpened(PluginChatEvent event) {
        enqueueChatEvent(event, true);
    }

    @Override
    public void chatClosed(PluginChatEvent event) {
        enqueueChatEvent(event, false);
    }

    private synchronized void enqueueChatEvent(PluginChatEvent event, boolean opened) {
        if (!lifecycle.isEnabled()
                || opened && !chatOpenedSubscribers
                || !opened && !chatClosedSubscribers) {
            return;
        }

        if (!eventSlots.tryAcquire()) {
            Log.w(TAG, "[" + id + "] Queue full; chat event skipped");

            return;
        }

        final long request = lifecycle.getGeneration();
        final long ownerId = event.ownerId;

        try {
            executor.execute(
                    () -> {
                        try {
                            if (!lifecycle.isCurrent(request) || instance == null) {
                                return;
                            }

                            if (UserConfig.getInstance(event.account).getClientUserId() != ownerId) {
                                return;
                            }

                            bridge.callAttr(
                                    opened ? "dispatch_chat_opened" : "dispatch_chat_closed",
                                    instance,
                                    event.account,
                                    ownerId,
                                    request,
                                    event.presentationId,
                                    event.dialogId,
                                    event.topicId
                            );
                        } catch (Exception | LinkageError error) {
                            Log.e(TAG, "[" + id + "] Chat event handler failed", error);
                        } finally {
                            eventSlots.release();
                        }
                    });
        } catch (RejectedExecutionException error) {
            eventSlots.release();
            Log.w(TAG, "[" + id + "] Chat event rejected", error);
        }
    }

    public synchronized void setChatOpenedSubscriptionActive(
            long generation,
            boolean active) {
        if (lifecycle.isCurrent(generation)) {
            chatOpenedSubscribers = active;
        }
    }

    @Override
    public boolean hasChatOpenedSubscribers() {
        return chatOpenedSubscribers;
    }

    public synchronized void setChatClosedSubscriptionActive(
            long generation,
            boolean active) {
        if (lifecycle.isCurrent(generation)) {
            chatClosedSubscribers = active;
        }
    }

    @Override
    public boolean hasChatClosedSubscribers() {
        return chatClosedSubscribers;
    }

    @Override
    public void applicationForegrounded(PluginApplicationEvent event) {
        enqueueApplicationEvent(event, true);
    }

    @Override
    public void applicationBackgrounded(PluginApplicationEvent event) {
        enqueueApplicationEvent(event, false);
    }

    private synchronized void enqueueApplicationEvent(
            PluginApplicationEvent event,
            boolean foregrounded) {
        if (!lifecycle.isEnabled()
                || foregrounded && !applicationForegroundedSubscribers
                || !foregrounded && !applicationBackgroundedSubscribers) {
            return;
        }

        if (!eventSlots.tryAcquire()) {
            Log.w(TAG, "[" + id + "] Queue full; application event skipped");

            return;
        }

        final long request = lifecycle.getGeneration();
        final long ownerId = event.ownerId;

        try {
            executor.execute(
                    () -> {
                        try {
                            if (!lifecycle.isCurrent(request) || instance == null) {
                                return;
                            }

                            if (UserConfig.getInstance(event.account).getClientUserId() != ownerId) {
                                return;
                            }

                            bridge.callAttr(
                                    foregrounded
                                            ? "dispatch_application_foregrounded"
                                            : "dispatch_application_backgrounded",
                                    instance,
                                    event.account,
                                    ownerId,
                                    request
                            );
                        } catch (Exception | LinkageError error) {
                            Log.e(TAG, "[" + id + "] Application event handler failed", error);
                        } finally {
                            eventSlots.release();
                        }
                    });
        } catch (RejectedExecutionException error) {
            eventSlots.release();
            Log.w(TAG, "[" + id + "] Application event rejected", error);
        }
    }

    public synchronized void setApplicationForegroundedSubscriptionActive(
            long generation,
            boolean active) {
        if (lifecycle.isCurrent(generation)) {
            applicationForegroundedSubscribers = active;
        }
    }

    @Override
    public boolean hasApplicationForegroundedSubscribers() {
        return applicationForegroundedSubscribers;
    }

    public synchronized void setApplicationBackgroundedSubscriptionActive(
            long generation,
            boolean active) {
        if (lifecycle.isCurrent(generation)) {
            applicationBackgroundedSubscribers = active;
        }
    }

    @Override
    public boolean hasApplicationBackgroundedSubscribers() {
        return applicationBackgroundedSubscribers;
    }
}
