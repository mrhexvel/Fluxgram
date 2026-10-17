package org.telegram.messenger.plugins;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.util.Log;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.plugins.packages.PluginPackageStore;
import org.telegram.messenger.plugins.packages.PluginPackageValidator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class PluginDebugProvider extends ContentProvider {
    private static final String TAG = "FluxGramDevBridge";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "FluxGramDevBridge"));

    private final Object lock = new Object();
    private Future<?> uploadTask;
    private File uploadedArchive;
    private String uploadedHash;
    private Exception uploadError;
    private CountDownLatch resultReady;
    private String resultJson;
    private boolean resultOpened;
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireShell();
        requireAvailable();

        try {
            if ("/archive".equals(uri.getPath()) && "w".equals(mode)) {
                return openArchivePipe();
            }

            if ("/result".equals(uri.getPath()) && "r".equals(mode)) {
                return openResultPipe();
            }
        } catch (IOException error) {
            FileNotFoundException failure = new FileNotFoundException(error.getMessage());
            failure.initCause(error);
            throw failure;
        }

        throw new FileNotFoundException("Unsupported plugin debug path");
    }

    private ParcelFileDescriptor openArchivePipe() throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        File archive = File.createTempFile("fgplugin-debug-", ".zip", getContext().getCacheDir());

        synchronized (lock) {
            if (uploadTask != null && !uploadTask.isDone() || resultReady != null) {
                pipe[0].close();
                pipe[1].close();
                archive.delete();
                throw new IOException("Plugin debug bridge is busy");
            }

            clearUploadLocked();
            uploadedArchive = archive;
            uploadTask = IO.submit(() -> receiveArchive(pipe[0], archive));
        }

        return pipe[1];
    }

    private void receiveArchive(ParcelFileDescriptor source, File archive) {
        Exception failure = null;
        String hash = null;

        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(source);
                FileOutputStream out = new FileOutputStream(archive)) {
            PluginPackageStore.copy(in, out, PluginPackageValidator.MAX_ARCHIVE_BYTES);
            out.getFD().sync();
            hash = sha256(archive);
        } catch (Exception error) {
            failure = error;
            archive.delete();
        }

        synchronized (lock) {
            uploadedHash = hash;
            uploadError = failure;
        }
    }

    private ParcelFileDescriptor openResultPipe() throws IOException {
        CountDownLatch ready;

        synchronized (lock) {
            if (resultOpened) {
                throw new IOException("Plugin debug result is already being read");
            }

            ready = resultReady;

            if (ready != null) {
                resultOpened = true;
            }
        }

        if (ready == null) {
            throw new IOException("No plugin debug operation is pending");
        }

        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        IO.execute(() -> writeResult(pipe[1], ready));
        return pipe[0];
    }

    private void writeResult(ParcelFileDescriptor target, CountDownLatch ready) {
        String value;
        boolean completed = false;

        try {
            if (!ready.await(30, TimeUnit.SECONDS)) {
                value = errorJson("Timeout", "Plugin debug operation timed out");
            } else {
                synchronized (lock) {
                    value = resultJson;
                    completed = resultReady == ready;
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            value = errorJson("Interrupted", "Plugin debug operation was interrupted");
        }

        try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(target)) {
            out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException error) {
            Log.e(TAG, "Cannot write plugin debug result", error);
        } finally {
            synchronized (lock) {
                if (completed && resultReady == ready) {
                    resultReady = null;
                    resultJson = null;
                    uploadTask = null;
                    clearUploadLocked();
                }

                resultOpened = false;
            }
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        requireShell();
        requireAvailable();

        if (!"install".equals(method) && !"reload".equals(method)) {
            throw new IllegalArgumentException("Unsupported plugin debug operation");
        }

        File archive;
        Future<?> task;

        synchronized (lock) {
            if (resultReady != null) {
                throw new IllegalStateException("Plugin debug bridge is busy");
            }

            archive = uploadedArchive;
            task = uploadTask;

            if (archive != null && task != null) {
                resultReady = new CountDownLatch(1);
            }
        }

        if (archive == null || task == null) {
            throw new IllegalStateException("Upload a plugin archive first");
        }

        try {
            task.get(15, TimeUnit.SECONDS);
        } catch (Exception error) {
            failClaim();
            throw new IllegalStateException("Plugin archive upload did not finish", error);
        }

        synchronized (lock) {
            if (uploadError != null) {
                failClaimLocked();
                throw new IllegalStateException("Plugin archive upload failed", uploadError);
            }

            if (arg == null || !arg.matches("[a-f0-9]{64}") || !arg.equals(uploadedHash)) {
                failClaimLocked();
                throw new IllegalArgumentException("Plugin archive hash mismatch");
            }
        }

        boolean reload = "reload".equals(method);
        boolean enable = extras != null && extras.getBoolean("enable", false);
        try {
            initializeApplication();
        } catch (RuntimeException error) {
            failClaim();
            throw error;
        }

        Log.i(TAG, "Plugin debug " + method + " accepted");
        CountDownLatch operation = resultReady;

        try {
            ExternalPlugins.developerInstallOrReload(archive, arg, reload, enable, (result, error) -> {
                String json = result == null
                        ? errorJson("Unavailable", "Plugin debug operation returned no result")
                        : result.toJson(error);

                synchronized (lock) {
                    if (resultReady == operation) {
                        resultJson = json;
                        operation.countDown();
                    }
                }

                Log.i(TAG, "Plugin debug " + method + " finished: "
                        + (error == null ? "success" : error.getClass().getSimpleName()));
            });
        } catch (RuntimeException error) {
            failClaim();
            throw error;
        }

        Bundle response = new Bundle();
        response.putBoolean("accepted", true);
        return response;
    }

    private void clearUploadLocked() {
        if (uploadedArchive != null) {
            uploadedArchive.delete();
        }

        uploadedArchive = null;
        uploadedHash = null;
        uploadError = null;
    }

    private void failClaim() {
        synchronized (lock) {
            failClaimLocked();
        }
    }

    private void failClaimLocked() {
        resultReady = null;
        resultJson = null;
        resultOpened = false;
    }

    private static void initializeApplication() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ApplicationLoader.postInitApplication();
            return;
        }

        CountDownLatch initialized = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ApplicationLoader.postInitApplication();
            } finally {
                initialized.countDown();
            }
        });

        try {
            if (!initialized.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("FluxGram initialization timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FluxGram initialization was interrupted", error);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];

        try (InputStream in = new FileInputStream(file)) {
            int count;

            while ((count = in.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }

        StringBuilder result = new StringBuilder(64);

        for (byte value : digest.digest()) {
            result.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
        }

        return result.toString();
    }

    private static String errorJson(String error, String message) {
        try {
            JSONObject result = new JSONObject();
            result.put("success", false);
            result.put("error", error);
            result.put("message", message);
            return result.toString();
        } catch (Exception impossible) {
            return "{\"success\":false,\"error\":\"Encoding\"}";
        }
    }

    private static void requireShell() {
        int uid = Binder.getCallingUid();

        if (uid != Process.SHELL_UID && uid != Process.ROOT_UID) {
            throw new SecurityException("Plugin debug bridge is shell-only");
        }
    }

    private void requireAvailable() {
        if (!PluginDeveloperBridge.isAvailable()) {
            throw new SecurityException(
                    "Enable temporary plugin developer access in FluxGram settings");
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }
}
