package org.telegram.messenger.plugins;

import android.content.Context;
import android.os.Build;
import android.os.Looper;

import org.fluxgram.JavaPluginModule;
import org.telegram.messenger.plugins.packages.PluginDexValidator;
import org.telegram.messenger.plugins.packages.PluginManifest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.lang.ref.WeakReference;

import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

final class JavaPluginLoader implements AutoCloseable {
    private final File cacheDirectory;
    private JavaPluginSession session;
    private WeakReference<ClassLoader> classLoader;
    private boolean hostReferencesReleased;

    private JavaPluginLoader(File cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    static JavaPluginLoader prepare(Context context, File directory, PluginManifest.JavaModule module,
            PluginSession pluginSession, JavaPluginSession.Host host) throws Exception {
        requireWorker();

        if (!module.isCompatible()) {
            throw new IOException("Incompatible Java module API");
        }

        File root = directory.getCanonicalFile();
        File source = new File(root, module.path).getCanonicalFile();

        if (!source.getPath().startsWith(root.getPath() + File.separator) || !source.isFile()) {
            throw new IOException("Invalid Java module path");
        }

        byte[] data;

        try (FileInputStream in = new FileInputStream(source)) {
            data = PluginDexValidator.read(in, module);
        }

        JavaPluginLoader result = new JavaPluginLoader(
                Build.VERSION.SDK_INT < 26 ? createCacheDirectory(context) : null);

        try {
            ClassLoader parent = JavaPluginModule.class.getClassLoader();
            ClassLoader loader;

            if (Build.VERSION.SDK_INT >= 26) {
                loader = new InMemoryDexClassLoader(ByteBuffer.wrap(data), parent);
            } else {
                File dex = new File(result.cacheDirectory, "module.dex");

                try (FileOutputStream out = new FileOutputStream(dex)) {
                    if (!dex.setReadOnly()) {
                        throw new IOException("Cannot mark Java module read-only");
                    }

                    out.write(data);
                    out.getFD().sync();
                }

                loader = new DexClassLoader(dex.getPath(), result.cacheDirectory.getPath(), null, parent);
            }

            Class<?> type = Class.forName(module.entrypoint, false, loader);

            if (type.getClassLoader() != loader) {
                throw new IOException("Java entrypoint resolved outside its module");
            }

            result.session = new JavaPluginSession(type, pluginSession, host);
            result.classLoader = new WeakReference<>(loader);
            return result;
        } catch (Exception | LinkageError error) {
            try {
                result.close();
            } catch (Exception cleanupError) {
                error.addSuppressed(cleanupError);
            }

            throw error;
        }
    }

    void start() throws Exception {
        requireWorker();
        session.start();
    }

    void settingChanged(String key, Object value) throws Exception {
        requireWorker();
        session.settingChanged(key, value);
    }

    @Override
    public void close() throws Exception {
        requireWorker();

        if (session != null) {
            session.close();
            session = null;
        }

        if (cacheDirectory != null) {
            deleteCache(cacheDirectory);
        }

        hostReferencesReleased = true;
    }

    boolean areHostReferencesReleased() {
        return hostReferencesReleased;
    }

    String classLoaderState() {
        if (classLoader == null) {
            return "not_applicable";
        }

        return classLoader.get() == null ? "cleared" : "release_eligible";
    }

    private static File createCacheDirectory(Context context) throws IOException {
        File file = File.createTempFile("fluxgram-dex-", "", context.getCodeCacheDir());

        if (!file.delete() || !file.mkdir()) {
            throw new IOException("Cannot create Java module cache");
        }

        return file;
    }

    private static void deleteCache(File file) throws IOException {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();

            if (children == null) {
                throw new IOException("Cannot list Java module cache");
            }

            for (File child : children) {
                deleteCache(child);
            }
        }

        if (!file.delete()) {
            throw new IOException("Cannot remove Java module cache");
        }
    }

    private static void requireWorker() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Java module loading requires a worker thread");
        }
    }
}
