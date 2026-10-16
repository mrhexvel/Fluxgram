package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class XposedBridge {
    private static final ConcurrentHashMap<Member, List<XC_MethodHook>> hooks =
            new ConcurrentHashMap<>();

    private XposedBridge() {
    }

    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook callback) {
        synchronized (hooks) {
            List<XC_MethodHook> callbacks = hooks.computeIfAbsent(member, key -> new ArrayList<>());
            callbacks.add(callback);
            callbacks.sort(Comparator.comparingInt((XC_MethodHook value) -> value.priority).reversed());
        }

        return callback.new Unhook(member);
    }

    public static Object invokeOriginalMethod(Member member, Object receiver, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException, InstantiationException {
        if (member instanceof Method) {
            return ((Method) member).invoke(receiver, arguments);
        }

        return ((Constructor<?>) member).newInstance(arguments);
    }

    static void unhook(Member member, XC_MethodHook callback) {
        synchronized (hooks) {
            List<XC_MethodHook> callbacks = hooks.get(member);

            if (callbacks == null) {
                return;
            }

            callbacks.remove(callback);

            if (callbacks.isEmpty()) {
                hooks.remove(member);
            }
        }
    }

    public static Object invokeHooked(Member member, Object receiver, Object[] arguments)
            throws Throwable {
        XC_MethodHook[] callbacks;

        synchronized (hooks) {
            List<XC_MethodHook> current = hooks.get(member);
            callbacks = current == null ? new XC_MethodHook[0]
                    : current.toArray(new XC_MethodHook[0]);
        }

        if (callbacks.length == 0) {
            return invokeOriginal(member, receiver, arguments);
        }

        XC_MethodHook.MethodHookParam parameter = new XC_MethodHook.MethodHookParam();
        parameter.method = member;
        parameter.thisObject = receiver;
        parameter.args = arguments;
        int beforeCount = 0;

        for (XC_MethodHook callback : callbacks) {
            callback.beforeHookedMethod(parameter);
            beforeCount++;

            if (parameter.returnEarly) {
                break;
            }
        }

        if (!parameter.returnEarly) {
            try {
                parameter.setResult(invokeOriginal(member, receiver, parameter.args));
            } catch (Throwable error) {
                parameter.setThrowable(error);
            }
        }

        for (int i = beforeCount - 1; i >= 0; i--) {
            callbacks[i].afterHookedMethod(parameter);
        }

        if (parameter.hasThrowable()) {
            throw parameter.getThrowable();
        }

        return parameter.getResult();
    }

    private static Object invokeOriginal(Member member, Object receiver, Object[] arguments)
            throws Throwable {
        try {
            return invokeOriginalMethod(member, receiver, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
