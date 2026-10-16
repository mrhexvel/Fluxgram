package org.telegram.messenger.plugins;

import org.telegram.messenger.plugins.api.JavaPluginContext;
import org.telegram.messenger.plugins.api.JavaPluginModule;
import org.telegram.messenger.plugins.api.JavaPluginUiResource;
import org.telegram.messenger.plugins.api.JavaMethodHook;
import org.telegram.messenger.plugins.api.JavaMethodHookRegistration;
import org.telegram.messenger.plugins.api.ComposerContentEffect;
import org.telegram.messenger.plugins.api.ComposerEffect;
import org.telegram.messenger.plugins.api.ComposerCursorEffect;
import org.telegram.messenger.plugins.api.ComposerSelectionEffect;
import org.telegram.messenger.plugins.api.ComposerTextEffect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

final class JavaPluginSession implements AutoCloseable {
    interface Host {
        boolean isActive(long generation);

        boolean isWorkerThread();

        boolean readBooleanSetting(String key, boolean fallback);

        int readIntSetting(String key, int fallback);

        String readStringSetting(String key, String fallback);

        void log(String message);

        default JavaMethodHookRegistration registerMethodHook(long generation,
                Member member, int priority, JavaMethodHook hook) {
            throw new UnsupportedOperationException("Method hooks are unavailable");
        }

        default void releaseMethodHookResource(JavaMethodHookRegistration registration,
                AutoCloseable resource) {
            throw new UnsupportedOperationException("Method hook cleanup is unavailable");
        }

        default JavaPluginUiResource registerUiResource(long generation, JavaPluginUiResource.Factory factory) {
            throw new UnsupportedOperationException("Java UI resources are unavailable");
        }

        default JavaPluginUiResource registerComposerEffect(long generation, ComposerEffect.Factory factory) {
            throw new UnsupportedOperationException("Composer effects are unavailable");
        }

        default JavaPluginUiResource registerComposerTextEffect(long generation, ComposerTextEffect.Factory factory) {
            throw new UnsupportedOperationException("Composer text effects are unavailable");
        }

        default JavaPluginUiResource registerComposerCursorEffect(
                long generation, ComposerCursorEffect.Factory factory) {
            throw new UnsupportedOperationException("Composer cursor effects are unavailable");
        }

        default JavaPluginUiResource registerComposerSelectionEffect(
                long generation, ComposerSelectionEffect.Factory factory) {
            throw new UnsupportedOperationException("Composer selection effects are unavailable");
        }

        default JavaPluginUiResource registerComposerContentEffect(
                long generation, ComposerContentEffect.Factory factory) {
            throw new UnsupportedOperationException("Composer content effects are unavailable");
        }

        default JavaPluginUiResource registerLowLevelUiResource(
                long generation, String extensionId, Object factory) {
            throw new UnsupportedOperationException("Low-level UI extensions are unavailable");
        }
    }

    private final Constructor<? extends JavaPluginModule> constructor;
    private final PluginSession pluginSession;
    private final boolean ownsPluginSession;
    private final Context context;
    private JavaPluginModule module;
    private boolean started;
    private boolean unloaded;

    JavaPluginSession(Class<?> type, String pluginId, long generation, Host host)
            throws ReflectiveOperationException {
        this(type, createPluginSession(pluginId, generation, host), host, true);
    }

    JavaPluginSession(Class<?> type, PluginSession pluginSession, Host host)
            throws ReflectiveOperationException {
        this(type, pluginSession, host, false);
    }

    private JavaPluginSession(Class<?> type, PluginSession pluginSession, Host host,
            boolean ownsPluginSession) throws ReflectiveOperationException {
        if (!JavaPluginModule.class.isAssignableFrom(type)
                || !Modifier.isPublic(type.getModifiers())
                || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("Java entrypoint must implement JavaPluginModule");
        }

        constructor = type.asSubclass(JavaPluginModule.class).getConstructor();
        this.pluginSession = pluginSession;
        this.ownsPluginSession = ownsPluginSession;
        context = new Context(pluginSession, host);
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
        Exception failure = null;
        boolean scopeClosed = false;

        try {
            context.scope.close();
            scopeClosed = true;
        } catch (Exception | LinkageError | AssertionError error) {
            failure = collect(failure, error);
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

        if (ownsPluginSession && scopeClosed && module == null) {
            try {
                pluginSession.close();
            } catch (Exception | LinkageError | AssertionError error) {
                failure = collect(failure, error);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static PluginSession createPluginSession(
            String pluginId, long generation, Host host) {
        return new PluginSession(
                pluginId,
                generation,
                () -> host.isActive(generation),
                () -> {
                    if (!host.isWorkerThread()) {
                        throw new IllegalStateException("Java module API requires the plugin worker");
                    }
                });
    }

    private static Exception collect(Exception failure, Throwable error) {
        if (failure == null) {
            return new Exception("Java module cleanup failed", error);
        }

        failure.addSuppressed(error);
        return failure;
    }

    private static final class Context implements JavaPluginContext, LowLevelJavaPluginContext {
        private static final int RESOURCE_LIMIT = 128;

        private final PluginSession session;
        private final Host host;
        private final PluginScope scope;

        Context(PluginSession session, Host host) {
            this.session = session;
            this.host = host;
            scope = session.openScope("java", this::requireThread, RESOURCE_LIMIT);
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
            return session.getPluginId();
        }

        @Override
        public long getGeneration() {
            return session.getGeneration();
        }

        @Override
        public boolean isActive() {
            return scope.isActive();
        }

        @Override
        public AutoCloseable registerResource(AutoCloseable resource) {
            requireActive();
            return scope.registerResource(resource);
        }

        @Override
        public JavaMethodHookRegistration registerMethodHook(
                Member member, int priority, JavaMethodHook hook) {
            requireActive();
            JavaMethodHookRegistration registration = host.registerMethodHook(
                    session.getGeneration(), member, priority, hook);

            try {
                AutoCloseable owner = scope.registerResource(registration);
                return new OwnedMethodHookRegistration(
                        registration, owner, host::isWorkerThread, host);
            } catch (RuntimeException | Error error) {
                try {
                    registration.close();
                } catch (Exception | LinkageError | AssertionError cleanupError) {
                    error.addSuppressed(cleanupError);
                }

                throw error;
            }
        }

        @Override
        public JavaPluginUiResource registerUiResource(JavaPluginUiResource.Factory factory) {
            requireActive();
            return host.registerUiResource(session.getGeneration(), factory);
        }

        @Override
        public JavaPluginUiResource registerComposerEffect(ComposerEffect.Factory factory) {
            requireActive();
            return host.registerComposerEffect(session.getGeneration(), factory);
        }

        @Override
        public JavaPluginUiResource registerComposerTextEffect(ComposerTextEffect.Factory factory) {
            requireActive();
            return host.registerComposerTextEffect(session.getGeneration(), factory);
        }

        @Override
        public JavaPluginUiResource registerComposerCursorEffect(ComposerCursorEffect.Factory factory) {
            requireActive();
            return host.registerComposerCursorEffect(session.getGeneration(), factory);
        }

        @Override
        public JavaPluginUiResource registerComposerSelectionEffect(ComposerSelectionEffect.Factory factory) {
            requireActive();
            return host.registerComposerSelectionEffect(session.getGeneration(), factory);
        }

        @Override
        public JavaPluginUiResource registerComposerContentEffect(ComposerContentEffect.Factory factory) {
            requireActive();
            return host.registerComposerContentEffect(session.getGeneration(), factory);
        }

        @Override
        public JavaPluginUiResource registerLowLevelUiResource(String extensionId, Object factory) {
            requireActive();
            return host.registerLowLevelUiResource(
                    session.getGeneration(), extensionId, factory);
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

    private static final class OwnedMethodHookRegistration implements JavaMethodHookRegistration {
        private final JavaMethodHookRegistration registration;
        private final AutoCloseable owner;
        private final BooleanSupplier workerThread;
        private final Host host;

        OwnedMethodHookRegistration(JavaMethodHookRegistration registration, AutoCloseable owner,
                BooleanSupplier workerThread, Host host) {
            this.registration = registration;
            this.owner = owner;
            this.workerThread = workerThread;
            this.host = host;
        }

        @Override
        public Member getMember() {
            return registration.getMember();
        }

        @Override
        public boolean isActive() {
            return registration.isActive();
        }

        @Override
        public void close() throws Exception {
            if (workerThread.getAsBoolean()) {
                owner.close();
            } else {
                host.releaseMethodHookResource(registration, owner);
            }
        }
    }
}
