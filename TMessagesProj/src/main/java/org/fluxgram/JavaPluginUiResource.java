package org.fluxgram;

public interface JavaPluginUiResource extends AutoCloseable {
    interface Factory {
        AutoCloseable create() throws Exception;
    }

    boolean isActive();

    boolean isClosed();

    @Override
    void close();
}
