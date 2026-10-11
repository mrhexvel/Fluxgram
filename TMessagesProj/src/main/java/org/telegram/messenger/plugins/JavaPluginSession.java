package org.telegram.messenger.plugins;

import org.telegram.messenger.plugins.api.JavaPluginContext;
import org.telegram.messenger.plugins.api.JavaPluginModule;
import org.telegram.messenger.plugins.api.JavaPluginUiResource;
import org.telegram.messenger.plugins.api.ComposerEffect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

final class JavaPluginSession implements AutoCloseable {
    interface Host {
        boolean isActive(long generation);

        boolean isWorkerThread();

        boolean readBooleanSetting(String key, boolean fallback);

        int readIntSetting(String key, int fallback);

        String readStringSetting(String key, String fallback);

        void log(String message);

        default JavaPluginUiResource registerUiResource(long generation, JavaPluginUiResource.Factory factory) {
            throw new UnsupportedOperationException("Java UI resources are unavailable");
        }

        default JavaPluginUiResource registerComposerEffect(long generation, ComposerEffect.Factory factory) {
            throw new UnsupportedOperationException("Composer effects are unavailable");
        }
    }

    private final Constructor<? extends JavaPluginModule> constructor;
    private final Context context;
    private JavaPluginModule module;
    private boolean started;
    private boolean unloaded;

    JavaPluginSession(Class<?> type, String pluginId, long generation, Host host)
            throws ReflectiveOperationException {
        if (!JavaPluginModule.class.isAssignableFrom(type)
                || !Modifier.isPublic(type.getModifiers())
                || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("Java entrypoint must implement JavaPluginModule");
        }

        constructor = type.asSubclass(JavaPluginModule.class).getConstructor();
        context = new Context(pluginId, generation, host);
    }

    void start() throws Exception {
        context.requireActive();

        if (started) {
            throw new IllegalStateException("Java module already started");
        }

        started = true;
        module = constructor.newInstance();
        context.requireActive();
        try {
            module.onLoad(context);
        } catch (LinkageError | AssertionError error) {
            throw new Exception("Java module load failed", error);
        }
    }

    void settingChanged(String key, Object value) throws Exception {
        context.requireThread();

        if (module != null && context.isActive()) {
            try {
                module.onSettingChanged(key, value);
            } catch (LinkageError | AssertionError error) {
                throw new Exception("Java setting callback failed", error);
            }
        }
    }

    @Override
    public void close() throws Exception {
        context.requireThread();
        context.closed = true;
        Exception failure = null;

        for (Resource resource : new ArrayList<>(context.resources)) {
            try {
                resource.close();
            } catch (Exception | LinkageError | AssertionError error) {
                failure = collect(failure, error);
            }
        }

        if (module != null && !unloaded) {
            try {
                module.onUnload();
                unloaded = true;
                module = null;
            } catch (Exception | LinkageError | AssertionError error) {
                failure = collect(failure, error);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static Exception collect(Exception failure, Throwable error) {
        if (failure == null) {
            return new Exception("Java module cleanup failed", error);
        }

        failure.addSuppressed(error);
        return failure;
    }

    private static final class Context implements JavaPluginContext {
        private final String pluginId;
        private final long generation;
        private final Host host;
        private final ArrayList<Resource> resources = new ArrayList<>();
        private volatile boolean closed;

        Context(String pluginId, long generation, Host host) {
            this.pluginId = pluginId;
            this.generation = generation;
            this.host = host;
        }

        void requireThread() {
            if (!host.isWorkerThread()) {
                throw new IllegalStateException("Java module API requires the plugin worker");
            }
        }

        void requireActive() {
            requireThread();

            if (!isActive()) {
                throw new IllegalStateException("Java module is inactive");
            }
        }

        @Override
        public String getPluginId() {
            return pluginId;
        }

        @Override
        public long getGeneration() {
            return generation;
        }

        @Override
        public boolean isActive() {
            return !closed && host.isActive(generation);
        }

        @Override
        public AutoCloseable registerResource(AutoCloseable resource) {
            requireActive();

            if (resource == null) {
                throw new IllegalArgumentException("Resource is required");
            }

            for (Resource existing : resources) {
                if (existing.delegate == resource) {
                    return existing;
                }
            }

            if (resources.size() >= 128) {
                throw new IllegalStateException("Java module resource limit reached");
            }

            Resource handle = new Resource(this, resource);
            resources.add(0, handle);
            return handle;
        }

        @Override
        public JavaPluginUiResource registerUiResource(JavaPluginUiResource.Factory factory) {
            requireActive();
            return host.registerUiResource(generation, factory);
        }

        @Override
        public JavaPluginUiResource registerComposerEffect(ComposerEffect.Factory factory) {
            requireActive();
            return host.registerComposerEffect(generation, factory);
        }

        @Override
        public boolean readBooleanSetting(String key, boolean fallback) {
            requireActive();
            return host.readBooleanSetting(key, fallback);
        }

        @Override
        public int readIntSetting(String key, int fallback) {
            requireActive();
            return host.readIntSetting(key, fallback);
        }

        @Override
        public String readStringSetting(String key, String fallback) {
            requireActive();
            return host.readStringSetting(key, fallback);
        }

        @Override
        public void log(String message) {
            host.log(message);
        }
    }

    private static final class Resource implements AutoCloseable {
        private final Context context;
        private AutoCloseable delegate;
        private boolean closing;

        Resource(Context context, AutoCloseable delegate) {
            this.context = context;
            this.delegate = delegate;
        }

        @Override
        public void close() throws Exception {
            context.requireThread();

            if (delegate == null || closing) {
                return;
            }

            closing = true;

            try {
                delegate.close();
                delegate = null;
                context.resources.remove(this);
            } finally {
                closing = false;
            }
        }
    }
}
