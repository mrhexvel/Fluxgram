package org.fluxgram;

public interface JavaMethodHook {
    int PRIORITY_LOWEST = -10000;
    int PRIORITY_DEFAULT = 50;
    int PRIORITY_HIGHEST = 10000;

    default void before(JavaMethodHookCall call) throws Throwable {
    }

    default void after(JavaMethodHookCall call) throws Throwable {
    }
}
