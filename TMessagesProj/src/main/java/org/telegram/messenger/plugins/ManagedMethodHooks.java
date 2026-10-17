package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;
import org.fluxgram.JavaMethodHook;
import org.fluxgram.JavaMethodHookCall;
import org.fluxgram.JavaMethodHookRegistration;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

final class ManagedMethodHooks {
    interface Lifecycle {
        boolean isActive();

        boolean commit(Runnable operation);
    }

    private static final long CALLBACK_BUDGET_NANOS = 4_000_000L;
    private static final int CALLBACK_OVERRUN_LIMIT = 3;
    private static final Object LOCK = new Object();
    private static final HashMap<HookKey, HookPoint> hookPoints = new HashMap<>();
    private static long nextSequence;

    private ManagedMethodHooks() {
    }

    static JavaMethodHookRegistration register(String pluginId, Lifecycle lifecycle,
            Member member, int priority, JavaMethodHook hook) {
        if (pluginId == null || pluginId.isEmpty()) {
            throw new IllegalArgumentException("Plugin ID is required");
        }

        if (!(member instanceof Method) && !(member instanceof Constructor)) {
            throw new IllegalArgumentException("Hook member must be a method or constructor");
        }

        if (lifecycle == null || hook == null) {
            throw new IllegalArgumentException("Hook lifecycle and callback are required");
        }

        if (priority < JavaMethodHook.PRIORITY_LOWEST
                || priority > JavaMethodHook.PRIORITY_HIGHEST) {
            throw new IllegalArgumentException("Hook priority is out of range");
        }

        synchronized (LOCK) {
            HookKey key = new HookKey(member, priority);
            HookPoint point = hookPoints.get(key);

            if (point == null || point.unhooking) {
                point = new HookPoint(key);
                hookPoints.put(key, point);
            }

            Registration registration = new Registration(
                    point, pluginId, lifecycle, priority, nextSequence++, hook);
            point.add(registration);
            return registration;
        }
    }

    static void deactivate(JavaMethodHookRegistration registration) {
        if (!(registration instanceof Registration)) {
            throw new IllegalArgumentException("Unknown method hook registration");
        }

        Registration managed = (Registration) registration;
        managed.point.deactivate(managed);
    }

    private static final class HookKey {
        final Member member;
        final int priority;

        HookKey(Member member, int priority) {
            this.member = member;
            this.priority = priority;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof HookKey)) {
                return false;
            }

            HookKey other = (HookKey) value;
            return priority == other.priority && member.equals(other.member);
        }

        @Override
        public int hashCode() {
            return 31 * member.hashCode() + priority;
        }
    }

    private static final class HookPoint {
        private static final Comparator<Registration> ORDER = (left, right) -> {
            int plugin = left.pluginId.compareTo(right.pluginId);
            return plugin != 0 ? plugin : Long.compare(left.sequence, right.sequence);
        };

        private final HookKey key;
        private final Member member;
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final ThreadLocal<ArrayDeque<Invocation>> invocations =
                ThreadLocal.withInitial(ArrayDeque::new);
        private volatile Registration[] snapshot = new Registration[0];
        private XC_MethodHook.Unhook unhook;
        private boolean unhooking;

        HookPoint(HookKey key) {
            this.key = key;
            member = key.member;
            XC_MethodHook callback = new XC_MethodHook(key.priority) {
                @Override
                protected void beforeHookedMethod(MethodHookParam parameter) {
                    dispatchBefore(parameter);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam parameter) {
                    dispatchAfter(parameter);
                }
            };
            unhook = XposedBridge.hookMethod(member, callback);

            if (unhook == null) {
                throw new IllegalStateException("Hook backend returned no registration");
            }
        }

        void add(Registration registration) {
            registrations.add(registration);
            rebuild();
        }

        void close(Registration registration) {
            XC_MethodHook.Unhook backend;

            synchronized (LOCK) {
                if (registration.closed) {
                    return;
                }

                if (registration.closing) {
                    throw new IllegalStateException("Method hook cleanup is in progress");
                }

                registration.closing = true;
                registration.active = false;
                registrations.remove(registration);
                rebuild();

                if (!registrations.isEmpty()) {
                    registration.closing = false;
                    registration.closed = true;
                    registration.releaseIfIdle();
                    return;
                }

                unhooking = true;
                backend = unhook;
            }

            try {
                backend.unhook();
            } catch (RuntimeException | Error error) {
                synchronized (LOCK) {
                    unhooking = false;
                    registration.closing = false;
                    registrations.add(registration);
                    rebuild();
                }

                throw error;
            }

            synchronized (LOCK) {
                unhooking = false;
                unhook = null;
                hookPoints.remove(key, this);
                registration.closing = false;
                registration.closed = true;
                registration.releaseIfIdle();
            }
        }

        void quarantine(Registration registration) {
            synchronized (LOCK) {
                registration.active = false;
                rebuild();
            }
        }

        void deactivate(Registration registration) {
            synchronized (LOCK) {
                registration.active = false;
                rebuild();
                registration.releaseIfIdle();
            }
        }

        private void rebuild() {
            snapshot = registrations.stream()
                    .filter(registration -> registration.active)
                    .sorted(ORDER)
                    .toArray(Registration[]::new);
        }

        private void dispatchBefore(XC_MethodHook.MethodHookParam parameter) {
            Invocation invocation = new Invocation();

            for (Registration registration : snapshot) {
                DispatchResult result = registration.dispatch(
                        parameter, JavaMethodHookCall.Phase.BEFORE);

                if (result.invoked) {
                    invocation.registrations.add(registration);
                }

                if (result.returnEarly) {
                    break;
                }
            }

            invocations.get().push(invocation);
        }

        private void dispatchAfter(XC_MethodHook.MethodHookParam parameter) {
            ArrayDeque<Invocation> stack = invocations.get();
            Invocation invocation = stack.isEmpty() ? null : stack.pop();

            if (stack.isEmpty()) {
                invocations.remove();
            }

            if (invocation == null) {
                return;
            }

            for (int i = invocation.registrations.size() - 1; i >= 0; i--) {
                invocation.registrations.get(i).dispatch(
                        parameter, JavaMethodHookCall.Phase.AFTER);
            }
        }
    }

    private static final class Invocation {
        final ArrayList<Registration> registrations = new ArrayList<>();
    }

    private static final class DispatchResult {
        static final DispatchResult SKIPPED = new DispatchResult(false, false);
        final boolean invoked;
        final boolean returnEarly;

        DispatchResult(boolean invoked, boolean returnEarly) {
            this.invoked = invoked;
            this.returnEarly = returnEarly;
        }
    }

    private static final class Registration implements JavaMethodHookRegistration {
        private final HookPoint point;
        private final String pluginId;
        private final int priority;
        private final long sequence;
        private final AtomicInteger inFlight = new AtomicInteger();
        private volatile Lifecycle lifecycle;
        private volatile JavaMethodHook hook;
        private volatile boolean active = true;
        private volatile boolean closing;
        private volatile boolean closed;
        private int overruns;

        Registration(HookPoint point, String pluginId, Lifecycle lifecycle,
                int priority, long sequence, JavaMethodHook hook) {
            this.point = point;
            this.pluginId = pluginId;
            this.lifecycle = lifecycle;
            this.priority = priority;
            this.sequence = sequence;
            this.hook = hook;
        }

        @Override
        public Member getMember() {
            return point.member;
        }

        @Override
        public boolean isActive() {
            Lifecycle gate = lifecycle;
            return active && gate != null && gate.isActive();
        }

        @Override
        public void close() {
            point.close(this);
        }

        DispatchResult dispatch(XC_MethodHook.MethodHookParam parameter,
                JavaMethodHookCall.Phase phase) {
            JavaMethodHook callback = enter();

            if (callback == null) {
                return DispatchResult.SKIPPED;
            }

            Call call = new Call(parameter, phase);
            long started = System.nanoTime();
            boolean completed = false;
            boolean returnEarly = false;

            try {
                PluginCrashRecovery.runExternalWorker(pluginId, () -> {
                    try {
                        if (phase == JavaMethodHookCall.Phase.BEFORE) {
                            callback.before(call);
                        } else {
                            callback.after(call);
                        }
                    } catch (Throwable error) {
                        ManagedMethodHooks.<RuntimeException>throwUnchecked(error);
                    }
                });
                completed = true;
                Lifecycle gate = lifecycle;

                if (gate != null) {
                    boolean[] applied = new boolean[1];
                    boolean committed = gate.commit(() -> {
                        synchronized (LOCK) {
                            if (active) {
                                applied[0] = call.apply(parameter);
                            }
                        }
                    });
                    returnEarly = committed && applied[0];
                }

                if (!returnEarly && call.hasInvokedOriginal()) {
                    returnEarly = call.applyOriginal(parameter);
                }
            } catch (Throwable error) {
                PluginCrashRecovery.handled(error);
                returnEarly = call.applyOriginal(parameter);

                if (!call.isOriginalThrowable(error)) {
                    quarantine(error);
                }
            } finally {
                call.invalidate();
                finish();

                if (completed && System.nanoTime() - started > CALLBACK_BUDGET_NANOS) {
                    recordOverrun();
                }
            }

            return new DispatchResult(true, returnEarly);
        }

        private JavaMethodHook enter() {
            if (!isActive()) {
                return null;
            }

            inFlight.incrementAndGet();
            JavaMethodHook callback = hook;

            if (!isActive() || callback == null) {
                finish();
                return null;
            }

            return callback;
        }

        private void finish() {
            if (inFlight.decrementAndGet() == 0) {
                releaseIfIdle();
            }
        }

        private void releaseIfIdle() {
            if ((!active || closed) && inFlight.get() == 0) {
                hook = null;
                lifecycle = null;
            }
        }

        private synchronized void recordOverrun() {
            if (!active || ++overruns < CALLBACK_OVERRUN_LIMIT) {
                return;
            }

            quarantine(new IllegalStateException("Method hook callback exceeded the 4 ms budget"));
        }

        private void quarantine(Throwable error) {
            point.quarantine(this);
            releaseIfIdle();
            FileLog.e(error);
        }
    }

    private static final class Call implements JavaMethodHookCall {
        private final Phase phase;
        private final Member member;
        private final Object thisObject;
        private final Object[] arguments;
        private Object result;
        private Throwable throwable;
        private Object originalResult;
        private Throwable originalThrowable;
        private boolean outcomeChanged;
        private boolean originalInvoked;
        private boolean valid = true;

        Call(XC_MethodHook.MethodHookParam parameter, Phase phase) {
            this.phase = phase;
            member = parameter.method;
            thisObject = parameter.thisObject;
            arguments = parameter.args == null ? new Object[0] : parameter.args.clone();

            if (phase == Phase.AFTER) {
                result = parameter.getResult();
                throwable = parameter.getThrowable();
            }
        }

        @Override
        public Phase getPhase() {
            requireValid();
            return phase;
        }

        @Override
        public Member getMember() {
            requireValid();
            return member;
        }

        @Override
        public Object getThisObject() {
            requireValid();
            return thisObject;
        }

        @Override
        public Object[] getArguments() {
            requireValid();
            return arguments;
        }

        @Override
        public Object getResult() {
            requireValid();
            return result;
        }

        @Override
        public Throwable getThrowable() {
            requireValid();
            return throwable;
        }

        @Override
        public boolean hasThrowable() {
            requireValid();
            return throwable != null;
        }

        @Override
        public void setResult(Object value) {
            requireValid();
            validateResult(member, value);
            result = value;
            throwable = null;
            outcomeChanged = true;
        }

        @Override
        public void setThrowable(Throwable value) {
            requireValid();

            if (value == null) {
                throw new IllegalArgumentException("Hook throwable is required");
            }

            throwable = value;
            result = null;
            outcomeChanged = true;
        }

        @Override
        public Object invokeOriginal() throws Throwable {
            requireValid();

            if (phase != Phase.BEFORE) {
                throw new IllegalStateException("Original invocation is only available before the call");
            }

            if (originalInvoked) {
                throw new IllegalStateException("Original method was already invoked");
            }

            validateArguments(member, arguments);
            originalInvoked = true;

            try {
                originalResult = XposedBridge.invokeOriginalMethod(member, thisObject, arguments);
                return originalResult;
            } catch (InvocationTargetException error) {
                originalThrowable = error.getCause();
                throw originalThrowable;
            } catch (Throwable error) {
                originalThrowable = error;
                throw error;
            }
        }

        boolean apply(XC_MethodHook.MethodHookParam parameter) {
            requireValid();
            applyArguments(parameter);

            if (outcomeChanged) {
                applyOutcome(parameter, result, throwable);
                return true;
            }

            return applyOriginal(parameter);
        }

        boolean applyOriginal(XC_MethodHook.MethodHookParam parameter) {
            if (!originalInvoked) {
                return false;
            }

            applyArguments(parameter);
            applyOutcome(parameter, originalResult, originalThrowable);
            return true;
        }

        boolean hasInvokedOriginal() {
            return originalInvoked;
        }

        boolean isOriginalThrowable(Throwable error) {
            return originalInvoked && originalThrowable == error;
        }

        void invalidate() {
            valid = false;
        }

        private void requireValid() {
            if (!valid) {
                throw new IllegalStateException("Method hook call is no longer active");
            }
        }

        private void applyArguments(XC_MethodHook.MethodHookParam parameter) {
            validateArguments(member, arguments);

            if (parameter.args == null) {
                if (arguments.length != 0) {
                    throw new IllegalStateException("Hook argument count changed");
                }
            } else {
                System.arraycopy(arguments, 0, parameter.args, 0, arguments.length);
            }
        }

        private static void applyOutcome(XC_MethodHook.MethodHookParam parameter,
                Object result, Throwable throwable) {
            if (throwable == null) {
                parameter.setResult(result);
            } else {
                parameter.setThrowable(throwable);
            }
        }
    }

    private static void validateArguments(Member member, Object[] arguments) {
        Class<?>[] types = member instanceof Method
                ? ((Method) member).getParameterTypes()
                : ((Constructor<?>) member).getParameterTypes();

        if (arguments.length != types.length) {
            throw new IllegalArgumentException("Hook argument count changed");
        }

        for (int i = 0; i < types.length; i++) {
            if (!accepts(types[i], arguments[i])) {
                throw new IllegalArgumentException("Invalid hook argument at index " + i);
            }
        }
    }

    private static void validateResult(Member member, Object result) {
        if (member instanceof Constructor) {
            if (result != null) {
                throw new IllegalArgumentException("Constructor hook result must be null");
            }

            return;
        }

        Class<?> type = ((Method) member).getReturnType();

        if (type == Void.TYPE) {
            if (result != null) {
                throw new IllegalArgumentException("Void hook result must be null");
            }
        } else if (!accepts(type, result)) {
            throw new IllegalArgumentException("Invalid hook result type");
        }
    }

    private static boolean accepts(Class<?> type, Object value) {
        if (!type.isPrimitive()) {
            return value == null || type.isInstance(value);
        }

        if (value == null) {
            return false;
        }

        if (type == Boolean.TYPE) {
            return value instanceof Boolean;
        } else if (type == Byte.TYPE) {
            return value instanceof Byte;
        } else if (type == Character.TYPE) {
            return value instanceof Character;
        } else if (type == Short.TYPE) {
            return value instanceof Short;
        } else if (type == Integer.TYPE) {
            return value instanceof Integer;
        } else if (type == Long.TYPE) {
            return value instanceof Long;
        } else if (type == Float.TYPE) {
            return value instanceof Float;
        } else if (type == Double.TYPE) {
            return value instanceof Double;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable error) throws T {
        throw (T) error;
    }
}
