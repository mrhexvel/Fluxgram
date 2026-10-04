package org.telegram.messenger.plugins;

import android.util.Log;

import com.chaquo.python.PyObject;

import org.json.JSONObject;
import org.telegram.messenger.LocaleController;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class PythonPluginAdapter implements Plugin {
    private static final String TAG = "FluxGramPython";

    private final String id;
    private final String moduleName;
    private final int nameResId;
    private final int descriptionResId;
    private final String version;

    private final ThreadPoolExecutor executor;

    private final Semaphore eventSlots = new Semaphore(64);

    private volatile boolean enabled;
    private volatile long generation;

    private PyObject bridge;
    private PyObject instance;

    public PythonPluginAdapter(
            String id,
            String moduleName,
            int nameResId,
            int descriptionResId,
            String version
    ) {
        this.id = id;
        this.moduleName = moduleName;
        this.nameResId = nameResId;
        this.descriptionResId = descriptionResId;
        this.version = version;

        executor = new ThreadPoolExecutor(
                1,
                1,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> new Thread(
                        runnable,
                        "FluxGramPlugin-" + id
                )
        );

        executor.allowCoreThreadTimeOut(true);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return LocaleController.getString(nameResId);
    }

    @Override
    public String getDescription() {
        return LocaleController.getString(descriptionResId);
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public synchronized void onEnabledChanged(boolean enabled) {
        this.enabled = enabled;
        final long request = ++generation;

        executor.execute(() -> {
            unload();

            if (!enabled || request != generation) {
                return;
            }

            try {
                bridge = PythonRuntime.prepare()
                        .getModule("fluxgram._bridge");

                if (request != generation) {
                    return;
                }

                instance = bridge.callAttr(
                        "create_plugin",
                        moduleName,
                        id,
                        this,
                        request
                );

                instance.callAttr("on_load");

                if (request != generation) {
                    unload();
                }
            } catch (Exception | LinkageError error) {
                Log.e(TAG, "[" + id + "] Load failed", error);
                unload();
            }
        });
    }

    public boolean isGenerationActive(long value) {
        return enabled && generation == value;
    }

    private void unload() {
        PyObject oldInstance = instance;
        instance = null;
        bridge = null;

        if (oldInstance == null) {
            return;
        }

        try {
            oldInstance.callAttr("_unload");
        } catch (Exception | LinkageError error) {
            Log.e(TAG, "[" + id + "] Unload failed", error);
        }
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
            executor.execute(() -> {
                try {
                    if (!enabled
                            || request != generation
                            || instance == null) {
                        return;
                    }

                    JSONObject payload = new JSONObject();
                    payload.put("account", account);
                    payload.put("text_length", textLength);
                    payload.put("schedule_date", scheduleDate);

                    bridge.callAttr(
                            "dispatch_before_send",
                            instance,
                            payload.toString()
                    );
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