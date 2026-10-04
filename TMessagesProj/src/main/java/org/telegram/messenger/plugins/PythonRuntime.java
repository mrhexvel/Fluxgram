package org.telegram.messenger.plugins;

import android.content.Context;
import android.os.Looper;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import org.telegram.messenger.ApplicationLoader;

/** A common Python preparation point. Called from a background thread. */
public final class PythonRuntime {
    private static final Object START_LOCK = new Object();

    private PythonRuntime() {
    }

    public static Python prepare() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "Prepare Python on a background thread"
            );
        }

        synchronized (START_LOCK) {
            if (!Python.isStarted()) {
                Context context = ApplicationLoader.applicationContext;

                if (context == null) {
                    throw new IllegalStateException(
                            "Application context is not available"
                    );
                }

                Python.start(new AndroidPlatform(context));
            }

            return Python.getInstance();
        }
    }
}