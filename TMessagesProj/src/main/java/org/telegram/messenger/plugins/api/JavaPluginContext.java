package org.telegram.messenger.plugins.api;

public interface JavaPluginContext {
    String getPluginId();

    long getGeneration();

    boolean isActive();

    AutoCloseable registerResource(AutoCloseable resource);

    boolean readBooleanSetting(String key, boolean fallback);

    int readIntSetting(String key, int fallback);

    String readStringSetting(String key, String fallback);

    void log(String message);
}
