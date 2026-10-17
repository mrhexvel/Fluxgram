package org.fluxgram;

import java.lang.reflect.Member;

public interface JavaMethodHookRegistration extends AutoCloseable {
    Member getMember();

    boolean isActive();

    @Override
    void close() throws Exception;
}
