package org.telegram.messenger.plugins;

import android.util.Log;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class PythonLoggingPlugin implements Plugin {
    public static final String ID = "fluxgram.python_logging";

    private static final String TAG = "FluxGramPython";

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64),
            runnable -> new Thread(runnable, "FluxGramPythonLogging")
    );

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return LocaleController.getString(R.string.FluxGramPythonLogging);
    }

    @Override
    public String getDescription() {
        return LocaleController.getString(
                R.string.FluxGramPythonLoggingInfo
        );
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void beforeSend(BeforeSendEvent event) {
        final int account = event.account;
        final int textLength = event.text.length();
        final int scheduleDate = event.scheduleDate;

        try {
            executor.execute(() ->
                    processEvent(account, textLength, scheduleDate)
            );
        } catch (RejectedExecutionException error) {
            Log.w(TAG, "[" + ID + "] Queue full; event skipped");
        }
    }

    private void processEvent(
            int account,
            int textLength,
            int scheduleDate
    ) {
        PluginManager manager = PluginManager.getInstance();

        if (!manager.isEnabled(ID)) {
            return;
        }

        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(
                        ApplicationLoader.applicationContext
                ));
            }

            if (!manager.isEnabled(ID)) {
                return;
            }

            JSONObject payload = new JSONObject();
            payload.put("account", account);
            payload.put("text_length", textLength);
            payload.put("schedule_date", scheduleDate);

            String result = Python.getInstance()
                    .getModule("fluxgram_logging")
                    .callAttr("before_send", payload.toString())
                    .toString();

            Log.i(TAG, "[" + ID + "] " + result);
        } catch (Exception | LinkageError error) {
            Log.e(TAG, "[" + ID + "] Handler failed", error);
        }
    }
}