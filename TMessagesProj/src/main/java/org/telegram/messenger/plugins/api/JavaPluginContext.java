package org.telegram.messenger.plugins.api;

public interface JavaPluginContext {
    String getPluginId();

    long getGeneration();

    boolean isActive();

    AutoCloseable registerResource(AutoCloseable resource);

    default JavaPluginUiResource registerUiResource(JavaPluginUiResource.Factory factory) {
        throw new UnsupportedOperationException("Java UI resources require API 1.1");
    }

    default JavaPluginUiResource registerComposerEffect(ComposerEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer effects require Java API 1.2");
    }

    default JavaPluginUiResource registerComposerTextEffect(ComposerTextEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer text effects require Java API 1.3");
    }

    default JavaPluginUiResource registerComposerCursorEffect(ComposerCursorEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer cursor effects require Java API 1.11");
    }

    boolean readBooleanSetting(String key, boolean fallback);

    int readIntSetting(String key, int fallback);

    String readStringSetting(String key, String fallback);

    void log(String message);
}
