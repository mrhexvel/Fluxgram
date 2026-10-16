package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.JavaMethodHook;
import org.telegram.messenger.plugins.api.JavaMethodHookCall;
import org.telegram.messenger.plugins.api.JavaMethodHookRegistration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XposedBridge;

public final class ManagedMethodHooksCheck {
    private static int effects;

    public static final class Target {
        public static int add(int left, int right) {
            return left + right;
        }

        public static int multiply(int left, int right) {
            return left * right;
        }

        public static int effect(int value) {
            effects++;
            return value;
        }

        public static int fail() {
            effects++;
            throw new IllegalStateException("target failure");
        }
    }

    public static void main(String[] args) throws Throwable {
        Method add = Target.class.getMethod("add", int.class, int.class);
        Gate gate = new Gate();
        AtomicReference<JavaMethodHookCall> retained = new AtomicReference<>();
        JavaMethodHookRegistration registration = ManagedMethodHooks.register(
                "test.hook", gate, add, JavaMethodHook.PRIORITY_DEFAULT,
                new JavaMethodHook() {
                    @Override
                    public void before(JavaMethodHookCall call) {
                        check(call.getPhase() == JavaMethodHookCall.Phase.BEFORE, "before phase");
                        call.getArguments()[0] = 4;
                        retained.set(call);
                    }

                    @Override
                    public void after(JavaMethodHookCall call) {
                        check(call.getPhase() == JavaMethodHookCall.Phase.AFTER, "after phase");
                        call.setResult((Integer) call.getResult() * 2);
                    }
                });
        check((Integer) XposedBridge.invokeHooked(add, null, new Object[] {1, 2}) == 12,
                "before and after changes");
        rejects(() -> retained.get().getArguments());
        check(registration.getMember() == add && registration.isActive(), "registration state");

        gate.setActive(false);
        check((Integer) XposedBridge.invokeHooked(add, null, new Object[] {1, 2}) == 3,
                "inactive generation fallback");
        gate.setActive(true);
        registration.close();
        check(!registration.isActive(), "manual close state");
        check((Integer) XposedBridge.invokeHooked(add, null, new Object[] {1, 2}) == 3,
                "manual unhook");

        Method multiply = Target.class.getMethod("multiply", int.class, int.class);
        JavaMethodHookRegistration original = ManagedMethodHooks.register(
                "test.original", gate, multiply, JavaMethodHook.PRIORITY_DEFAULT,
                new JavaMethodHook() {
                    @Override
                    public void before(JavaMethodHookCall call) throws Throwable {
                        call.setResult((Integer) call.invokeOriginal() + 5);
                    }
                });
        check((Integer) XposedBridge.invokeHooked(multiply, null, new Object[] {3, 4}) == 17,
                "explicit original invocation");
        original.close();

        JavaMethodHookRegistration broken = ManagedMethodHooks.register(
                "test.failure", gate, add, JavaMethodHook.PRIORITY_DEFAULT,
                new JavaMethodHook() {
                    @Override
                    public void before(JavaMethodHookCall call) {
                        call.getArguments()[0] = "invalid";
                    }
                });
        check((Integer) XposedBridge.invokeHooked(add, null, new Object[] {2, 3}) == 5,
                "invalid callback fallback");
        check(!broken.isActive() && PluginCrashRecovery.handled == 1 && FileLog.errors == 1,
                "callback quarantine and attribution");
        check((Integer) XposedBridge.invokeHooked(add, null, new Object[] {3, 4}) == 7,
                "quarantined callback fallback");
        broken.close();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JavaMethodHookRegistration inFlight = ManagedMethodHooks.register(
                "test.inflight", gate, add, JavaMethodHook.PRIORITY_DEFAULT,
                new JavaMethodHook() {
                    @Override
                    public void before(JavaMethodHookCall call) throws Exception {
                        entered.countDown();
                        release.await();
                    }
                });
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                XposedBridge.invokeHooked(add, null, new Object[] {4, 5});
            } catch (Throwable error) {
                threadFailure.set(error);
            }
        });
        thread.start();
        entered.await();
        inFlight.close();
        release.countDown();
        thread.join();
        check(threadFailure.get() == null, "in-flight callback failed");
        check((Integer) XposedBridge.invokeHooked(add, null, new Object[] {4, 5}) == 9,
                "in-flight unhook");

        CountDownLatch staleEntered = new CountDownLatch(1);
        CountDownLatch staleRelease = new CountDownLatch(1);
        JavaMethodHookRegistration stale = ManagedMethodHooks.register(
                "test.stale", gate, add, JavaMethodHook.PRIORITY_DEFAULT,
                new JavaMethodHook() {
                    @Override
                    public void before(JavaMethodHookCall call) throws Exception {
                        staleEntered.countDown();
                        staleRelease.await();
                        call.getArguments()[0] = 50;
                    }
                });
        AtomicReference<Object> staleResult = new AtomicReference<>();
        Thread staleThread = new Thread(() -> {
            try {
                staleResult.set(XposedBridge.invokeHooked(add, null, new Object[] {2, 3}));
            } catch (Throwable error) {
                threadFailure.set(error);
            }
        });
        staleThread.start();
        staleEntered.await();
        gate.setActive(false);
        staleRelease.countDown();
        staleThread.join();
        check(threadFailure.get() == null && (Integer) staleResult.get() == 5,
                "generation invalidation during callback");
        gate.setActive(true);
        stale.close();

        Method effect = Target.class.getMethod("effect", int.class);
        effects = 0;
        JavaMethodHookRegistration originalThenFailure = ManagedMethodHooks.register(
                "test.original_failure", gate, effect, JavaMethodHook.PRIORITY_DEFAULT,
                new JavaMethodHook() {
                    @Override
                    public void before(JavaMethodHookCall call) throws Throwable {
                        call.invokeOriginal();
                        throw new IllegalArgumentException("plugin failure");
                    }
                });
        check((Integer) XposedBridge.invokeHooked(effect, null, new Object[] {8}) == 8
                        && effects == 1,
                "original invocation after callback failure ran twice");
        check(!originalThenFailure.isActive(), "original callback failure quarantine");
        originalThenFailure.close();

        Method fail = Target.class.getMethod("fail");
        effects = 0;
        JavaMethodHookRegistration originalThrowable = ManagedMethodHooks.register(
                "test.original_throwable", gate, fail, JavaMethodHook.PRIORITY_DEFAULT,
                new JavaMethodHook() {
                    @Override
                    public void before(JavaMethodHookCall call) throws Throwable {
                        call.invokeOriginal();
                    }
                });
        rejectsThrowable(() -> XposedBridge.invokeHooked(fail, null, new Object[0]));
        check(effects == 1 && originalThrowable.isActive(), "original throwable handling");
        originalThrowable.close();

        List<String> order = new ArrayList<>();
        JavaMethodHookRegistration z = ManagedMethodHooks.register(
                "z.plugin", gate, add, JavaMethodHook.PRIORITY_DEFAULT,
                ordered(order, "z"));
        JavaMethodHookRegistration a = ManagedMethodHooks.register(
                "a.plugin", gate, add, JavaMethodHook.PRIORITY_DEFAULT,
                ordered(order, "a"));
        check((Integer) XposedBridge.invokeHooked(add, null, new Object[] {1, 1}) == 2,
                "ordered hook result");
        check(order.equals(Arrays.asList("a.before", "z.before", "z.after", "a.after")),
                "deterministic hook order: " + order);
        z.close();
        a.close();

        order.clear();
        JavaMethodHookRegistration low = ManagedMethodHooks.register(
                "low.plugin", gate, add, JavaMethodHook.PRIORITY_LOWEST,
                ordered(order, "low"));
        JavaMethodHookRegistration high = ManagedMethodHooks.register(
                "high.plugin", gate, add, JavaMethodHook.PRIORITY_HIGHEST,
                ordered(order, "high"));
        XposedBridge.invokeHooked(add, null, new Object[] {1, 1});
        check(order.equals(Arrays.asList(
                        "high.before", "low.before", "low.after", "high.after")),
                "managed priority order: " + order);
        low.close();
        high.close();

        rejects(() -> ManagedMethodHooks.register(
                "test.priority", gate, add, JavaMethodHook.PRIORITY_HIGHEST + 1,
                new JavaMethodHook() {}));
    }

    private static JavaMethodHook ordered(List<String> order, String name) {
        return new JavaMethodHook() {
            @Override
            public void before(JavaMethodHookCall call) {
                order.add(name + ".before");
            }

            @Override
            public void after(JavaMethodHookCall call) {
                order.add(name + ".after");
            }
        };
    }

    private static void rejects(ThrowingOperation operation) throws Exception {
        try {
            operation.run();
        } catch (Exception expected) {
            return;
        }

        throw new AssertionError("Expected rejection");
    }

    private static void rejectsThrowable(ThrowableOperation operation) throws Exception {
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            return;
        } catch (Throwable error) {
            throw new Exception(error);
        }

        throw new AssertionError("Expected target throwable");
    }

    private static void check(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
    }

    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private interface ThrowableOperation {
        void run() throws Throwable;
    }

    private static final class Gate implements ManagedMethodHooks.Lifecycle {
        private boolean active = true;

        @Override
        public synchronized boolean isActive() {
            return active;
        }

        @Override
        public synchronized boolean commit(Runnable operation) {
            if (!active) {
                return false;
            }

            operation.run();
            return true;
        }

        synchronized void setActive(boolean value) {
            active = value;
        }
    }
}
