package org.telegram.messenger.plugins.api;

import java.lang.reflect.Member;

public interface JavaMethodHookRegistration extends AutoCloseable {
    Member getMember();

    boolean isActive();

    @Override
    void close() throws Exception;
}
