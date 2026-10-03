package org.telegram.messenger.plugins;

import android.content.Context;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.DispatchQueue;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PythonSmokeTest {
    private static final String TAG = "FluxGramPython";

    private static final AtomicBoolean scheduled =
            new AtomicBoolean(false);

    private static final DispatchQueue queue =
            new DispatchQueue("FluxGramPythonProbe");

    private PythonSmokeTest() {
    }

    public static void run(Context context) {
        if (!scheduled.compareAndSet(false, true)) {
            return;
        }

        Context appContext = context.getApplicationContext();

        queue.postRunnable(() -> {
            try {
                Log.i(TAG, "Starting Python probe");

                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(appContext));
                }

                Python python = Python.getInstance();
                PyObject module = python.getModule("fluxgram_probe");

                String result = module
                        .callAttr("hello", "FluxGram")
                        .toString();

                Log.i(TAG, result);
            } catch (Exception | LinkageError error) {
                Log.e(TAG, "Python probe failed", error);
            }
        });
    }
}