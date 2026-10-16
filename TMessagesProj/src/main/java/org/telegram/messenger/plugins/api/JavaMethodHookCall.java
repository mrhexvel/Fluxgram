package org.telegram.messenger.plugins.api;

import java.lang.reflect.Member;

public interface JavaMethodHookCall {
    enum Phase {
        BEFORE,
        AFTER
    }

    Phase getPhase();

    Member getMember();

    Object getThisObject();

    Object[] getArguments();

    Object getResult();

    Throwable getThrowable();

    boolean hasThrowable();

    void setResult(Object result);

    void setThrowable(Throwable throwable);

    Object invokeOriginal() throws Throwable;
}
