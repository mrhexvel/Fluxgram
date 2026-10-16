package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public final int priority;

    public XC_MethodHook(int priority) {
        this.priority = priority;
    }

    protected void beforeHookedMethod(MethodHookParam parameter) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam parameter) throws Throwable {
    }

    public final class Unhook {
        private final Member member;

        Unhook(Member member) {
            this.member = member;
        }

        public void unhook() {
            XposedBridge.unhook(member, XC_MethodHook.this);
        }
    }

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        boolean returnEarly;
        private Object result;
        private Throwable throwable;

        public Object getResult() {
            return result;
        }

        public void setResult(Object value) {
            result = value;
            throwable = null;
            returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable value) {
            throwable = value;
            result = null;
            returnEarly = true;
        }
    }
}
