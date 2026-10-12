package org.telegram.messenger.plugins;

import org.telegram.messenger.plugins.api.JavaPluginContext;
import org.telegram.messenger.plugins.api.JavaPluginModule;
import org.telegram.messenger.plugins.api.JavaPluginUiResource;
import org.telegram.messenger.plugins.api.ComposerSelectionEffect;
import org.telegram.messenger.plugins.api.ComposerTextEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class JavaPluginSessionCheck {
    private static final List<String> events = new ArrayList<>();
    private static JavaPluginContext context;
    private static int initialized;
    private static boolean failLoad;
    private static boolean failResource;
    private static boolean failUnload;

    public static final class Module implements JavaPluginModule {
        static {
            initialized++;
        }

        public Module() {
            events.add("construct");
        }

        @Override
        public void onLoad(JavaPluginContext value) throws Exception {
            context = value;
            AutoCloseable first = () -> events.add("first");
            AutoCloseable handle = context.registerResource(first);
            check(handle == context.registerResource(first), "duplicate resource registration");
            context.registerResource(() -> {
                events.add("second");

                if (failResource) {
                    failResource = false;
                    throw new Exception("resource failure");
                }
            });
            events.add("load");

            if (failLoad) {
                throw new Exception("load failure");
            }
        }

        @Override
        public void onSettingChanged(String key, Object value) {
            events.add(key + "=" + value);
        }

        @Override
        public void onUnload() throws Exception {
            check(!context.isActive(), "cleanup must invalidate context");
            context.log("unload");
            events.add("unload");

            if (failUnload) {
                failUnload = false;
                throw new Exception("unload failure");
            }
        }
    }

    public static final class Host implements JavaPluginSession.Host {
        long generation = 1;
        Thread worker = Thread.currentThread();
        int textRegistrations;
        int selectionRegistrations;

        @Override
        public boolean isActive(long value) {
            return value == generation;
        }

        @Override
        public boolean isWorkerThread() {
            return Thread.currentThread() == worker;
        }

        @Override
        public boolean readBooleanSetting(String key, boolean fallback) {
            return !fallback;
        }

        @Override
        public int readIntSetting(String key, int fallback) {
            return fallback + 1;
        }

        @Override
        public String readStringSetting(String key, String fallback) {
            return fallback + "!";
        }

        @Override
        public void log(String message) {
        }

        @Override
        public JavaPluginUiResource registerComposerTextEffect(long value, ComposerTextEffect.Factory factory) {
            check(value == generation && factory != null, "text registration identity");
            textRegistrations++;
            return new JavaPluginUiResource() {
                @Override
                public boolean isActive() {
                    return true;
                }

                @Override
                public boolean isClosed() {
                    return false;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public JavaPluginUiResource registerComposerSelectionEffect(
                long value, ComposerSelectionEffect.Factory factory) {
            check(value == generation && factory != null, "selection registration identity");
            selectionRegistrations++;
            return new JavaPluginUiResource() {
                @Override
                public boolean isActive() {
                    return true;
                }

                @Override
                public boolean isClosed() {
                    return false;
                }

                @Override
                public void close() {
                }
            };
        }
    }

    public static final class BrokenModule implements JavaPluginModule {
        @Override
        public void onLoad(JavaPluginContext value) {
            throw new AssertionError("load assertion");
        }

        @Override
        public void onSettingChanged(String key, Object value) {
            throw new AssertionError("setting assertion");
        }

        @Override
        public void onUnload() {
            throw new AssertionError("unload assertion");
        }
    }

    public static void main(String[] args) throws Exception {
        Host host = new Host();
        Class<?> type = Class.forName(Module.class.getName(), false, Module.class.getClassLoader());
        JavaPluginSession session = new JavaPluginSession(type, "test.module", 1, host);
        check(initialized == 0, "preparation initialized entrypoint");
        session.start();
        check(initialized == 1, "entrypoint did not initialize");
        check(context.getPluginId().equals("test.module") && context.getGeneration() == 1, "identity");
        check(context.readBooleanSetting("enabled", false), "boolean setting");
        check(context.readIntSetting("count", 4) == 5, "integer setting");
        check(context.readStringSetting("name", "x").equals("x!"), "string setting");
        context.registerComposerTextEffect(surface -> null);
        check(host.textRegistrations == 1, "text effect registration was not delegated");
        context.registerComposerSelectionEffect(surface -> null);
        check(host.selectionRegistrations == 1, "selection effect registration was not delegated");
        session.settingChanged("enabled", true);
        host.generation++;
        session.settingChanged("stale", true);
        rejects(() -> context.registerResource(() -> {}));
        rejects(() -> context.registerComposerTextEffect(surface -> null));
        rejects(() -> context.registerComposerSelectionEffect(surface -> null));
        rejects(() -> context.readIntSetting("count", 0));
        session.close();
        session.close();
        check(events.equals(Arrays.asList("construct", "load", "enabled=true", "second", "first", "unload")),
                "ordering or stale delivery: " + events);

        events.clear();
        failLoad = true;
        failResource = true;
        failUnload = true;
        JavaPluginSession failed = new JavaPluginSession(type, "test.module", host.generation, host);
        rejects(failed::start);
        rejects(failed::close);
        check(events.contains("first") && events.contains("unload"), "cleanup stopped at first error");
        failed.close();
        failed.close();
        check(events.equals(Arrays.asList("construct", "load", "second", "first", "unload", "second", "unload")),
                "cleanup retry repeated successful steps: " + events);

        failLoad = false;
        events.clear();
        JavaPluginSession restarted = new JavaPluginSession(type, "test.module", host.generation, host);
        restarted.start();

        for (int i = 0; i < 126; i++) {
            context.registerResource(new AutoCloseable() {
                @Override
                public void close() {
                }
            });
        }

        rejects(() -> context.registerResource(() -> {}));
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            try {
                rejects(() -> context.readIntSetting("count", 0));
                rejects(restarted::close);
            } catch (Throwable error) {
                threadFailure.set(error);
            }
        });
        foreign.start();
        foreign.join();
        check(threadFailure.get() == null, "foreign thread check failed");

        Thread replacement = new Thread(() -> {
            host.worker = Thread.currentThread();

            try {
                restarted.close();
            } catch (Throwable error) {
                threadFailure.set(error);
            }
        });
        replacement.start();
        replacement.join();
        check(threadFailure.get() == null, "replacement executor thread could not clean up");
        host.worker = Thread.currentThread();
        rejects(() -> new JavaPluginSession(String.class, "test.module", 1, host));
        JavaPluginSession stale = new JavaPluginSession(type, "test.module", 1, host);
        rejects(stale::start);
        stale.close();
        JavaPluginSession broken = new JavaPluginSession(BrokenModule.class, "test.broken", host.generation, host);
        rejects(broken::start);
        rejects(() -> broken.settingChanged("key", true));
        rejects(broken::close);
    }

    private static void rejects(AutoCloseable action) throws Exception {
        try {
            action.close();
        } catch (Exception expected) {
            return;
        }

        throw new AssertionError("Expected rejection");
    }

    private static void check(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
    }
}
