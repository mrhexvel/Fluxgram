package org.telegram.messenger.plugins;

import android.util.Log;

import com.chaquo.python.PyObject;

import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class PythonPluginAdapter implements Plugin {
    private static final String TAG = "FluxGramPython";
    private final String id;
    private final String moduleName;
    private final int nameResId;
    private final int descriptionResId;
    private final String version;
    private String externalPath, externalName, externalDescription;
    private volatile String loadError;
    private File iconFile;
    private volatile java.util.List<PluginSettings.Setting> settingsSchema;

    public PythonPluginAdapter(
            org.telegram.messenger.plugins.packages.PluginPackageStore.Installed installed) {
        this(
                installed.manifest.id,
                installed.manifest.entrypoint.split(":")[0],
                0,
                0,
                installed.manifest.version);
        externalPath = installed.directory.getAbsolutePath();
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
        return "fluxgram.in_app_notifications".equals(id)
                ? R.drawable.msg_notifications : 0;
    }

    public boolean isExternal() {
        return externalPath != null;
    }

    public String getLoadError() {
        return loadError;
    }

    private final ThreadPoolExecutor executor;
    private final Semaphore eventSlots = new Semaphore(64);
    private volatile boolean enabled;
    private volatile long generation;
    private PyObject bridge;
    private PyObject instance;
    private FutureTask<Void> removal;

    public PythonPluginAdapter(
            String id, String moduleName, int nameResId, int descriptionResId, String version) {
        this.id = id;
        this.moduleName = moduleName;
        this.nameResId = nameResId;
        this.descriptionResId = descriptionResId;
        this.version = version;
        settingsSchema = PluginSettings.readSchema(id, version);

        executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        30L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(),
                        runnable -> new Thread(runnable, "FluxGramPlugin-" + id));

        executor.allowCoreThreadTimeOut(true);
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
        if (removal != null) {
            return;
        }

        this.enabled = enabled;
        loadError = null;

        final long request = ++generation;

        executor.execute(
                () -> {
                    if (!unload()) {
                        this.enabled = false;
                        return;
                    }

                    if (!enabled || request != generation) {
                        return;
                    }

                    try {
                        bridge = PythonRuntime.prepare().getModule("fluxgram._bridge");

                        if (request != generation) {
                            return;
                        }

                        instance =
                                isExternal()
                                        ? bridge.callAttr(
                                                "create_external_plugin",
                                                externalPath,
                                                moduleName,
                                                id,
                                                this,
                                                request)
                                        : bridge.callAttr(
                                                "create_plugin", moduleName, id, this, request);

                        String schema = bridge.callAttr("describe_settings", instance).toString();
                        settingsSchema = PluginSettings.parse(schema);
                        PluginSettings.saveSchema(id, version, schema);
                        ExternalPlugins.changed();
                        instance.callAttr("on_load");

                        if (request != generation) {
                            unload();
                        }
                    } catch (Exception | LinkageError error) {
                        Log.e(TAG, "[" + id + "] Load failed", error);

                        if (request == generation) {
                            loadError = error.toString();

                            this.enabled = false;

                            org.telegram.messenger.AndroidUtilities.runOnUIThread(
                                    () -> {
                                        if (request == generation) {
                                            PluginManager.getInstance().setEnabled(id, false);
                                            loadError = error.toString();
                                            ExternalPlugins.changed();
                                        }
                                    });
                        }

                        unload();
                    }
                });
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
        long request = generation;

        if (enabled) {
            executor.execute(() -> {
                if (!isGenerationActive(request) || instance == null) {
                    return;
                }

                try {
                    bridge.callAttr("dispatch_setting_changed", instance, key, value);
                } catch (Exception | LinkageError error) {
                    Log.e(TAG, "[" + id + "] Setting callback failed", error);
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
                enabled = false;
                generation++;
                removal = new FutureTask<>(() -> {
                    unloadStrict();
                    executor.shutdown();
                    return null;
                });
                executor.execute(removal);
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
        return enabled && generation == value;
    }

    private boolean unload() {
        try {
            unloadStrict();
            return true;
        } catch (Exception | LinkageError error) {
            loadError = error.toString();
            Log.e(TAG, "[" + id + "] Unload failed", error);
            return false;
        }
    }

    private void unloadStrict() {
        if (instance == null) {
            return;
        }

        bridge.callAttr("unload_plugin", instance);
        instance = null;
        bridge = null;
    }

    @Override
    public synchronized void beforeSend(BeforeSendEvent event) {
        if (!enabled) {
            return;
        }

        if (!eventSlots.tryAcquire()) {
            Log.w(TAG, "[" + id + "] Queue full; event skipped");

            return;
        }

        final long request = generation;
        final int account = event.account;
        final int textLength = event.text.length();
        final int scheduleDate = event.scheduleDate;

        try {
            executor.execute(
                    () -> {
                        try {
                            if (!enabled || request != generation || instance == null) {
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
}
