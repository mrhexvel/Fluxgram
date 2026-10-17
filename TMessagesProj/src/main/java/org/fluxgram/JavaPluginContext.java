package org.fluxgram;

import java.lang.reflect.Member;

public interface JavaPluginContext {
    String getPluginId();

    long getGeneration();

    boolean isActive();

    AutoCloseable registerResource(AutoCloseable resource);

    default JavaMethodHookRegistration registerMethodHook(Member member, JavaMethodHook hook) {
        return registerMethodHook(member, JavaMethodHook.PRIORITY_DEFAULT, hook);
    }

    default JavaMethodHookRegistration registerMethodHook(
            Member member, int priority, JavaMethodHook hook) {
        throw new UnsupportedOperationException("Method hooks require Java API 2.0");
    }

    default JavaPluginUiResource registerUiResource(JavaPluginUiResource.Factory factory) {
        throw new UnsupportedOperationException("Java UI resources require API 2.0");
    }

    default JavaPluginUiResource registerComposerEffect(ComposerEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer effects require Java API 2.0");
    }

    default JavaPluginUiResource registerComposerTextEffect(ComposerTextEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer text effects require Java API 2.0");
    }

    default JavaPluginUiResource registerComposerCursorEffect(ComposerCursorEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer cursor effects require Java API 2.0");
    }

    default JavaPluginUiResource registerComposerSelectionEffect(ComposerSelectionEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer selection effects require Java API 2.0");
    }

    default JavaPluginUiResource registerComposerContentEffect(ComposerContentEffect.Factory factory) {
        throw new UnsupportedOperationException("Composer content effects require Java API 2.0");
    }

    default JavaPluginUiResource registerChatSelectionAction(
            String id, String title, String icon, ChatSelectionAction action) {
        throw new UnsupportedOperationException("Chat selection actions require Java API 2.0");
    }

    boolean readBooleanSetting(String key, boolean fallback);

    int readIntSetting(String key, int fallback);

    String readStringSetting(String key, String fallback);

    void log(String message);
}
