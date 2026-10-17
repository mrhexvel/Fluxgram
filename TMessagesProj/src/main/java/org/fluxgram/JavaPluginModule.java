package org.fluxgram;

public interface JavaPluginModule {
    void onLoad(JavaPluginContext context) throws Exception;

    default void onSettingChanged(String key, Object value) throws Exception {
    }

    void onUnload() throws Exception;
}
