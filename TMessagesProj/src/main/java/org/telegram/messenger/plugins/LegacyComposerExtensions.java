package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.ComposerContentEffect;
import org.telegram.messenger.plugins.api.ComposerCursorEffect;
import org.telegram.messenger.plugins.api.ComposerEffect;
import org.telegram.messenger.plugins.api.ComposerSelectionEffect;
import org.telegram.messenger.plugins.api.ComposerTextEffect;

import java.util.function.BooleanSupplier;

final class LegacyComposerExtensions {
    private static final HostExtensionDescriptor<Object, Void> EFFECT = descriptor(
            "ui.chat.composer.effect.compat");
    private static final HostExtensionDescriptor<Object, Void> TEXT = descriptor(
            "ui.chat.composer.text.compat");
    private static final HostExtensionDescriptor<Object, Void> CURSOR = descriptor(
            "ui.chat.composer.cursor.compat");
    private static final HostExtensionDescriptor<Object, Void> SELECTION = descriptor(
            "ui.chat.composer.selection.compat");
    private static final HostExtensionDescriptor<Object, Void> CONTENT = descriptor(
            "ui.chat.composer.content.compat");
    private static final HostExtensionRegistry REGISTRY =
            HostExtensionAttribution.create(FileLog::e);
    private static final HostExtensionRegistry.Endpoint<Object, Void> EFFECT_ENDPOINT =
            REGISTRY.openEndpoint(EFFECT);
    private static final HostExtensionRegistry.Endpoint<Object, Void> TEXT_ENDPOINT =
            REGISTRY.openEndpoint(TEXT);
    private static final HostExtensionRegistry.Endpoint<Object, Void> CURSOR_ENDPOINT =
            REGISTRY.openEndpoint(CURSOR);
    private static final HostExtensionRegistry.Endpoint<Object, Void> SELECTION_ENDPOINT =
            REGISTRY.openEndpoint(SELECTION);
    private static final HostExtensionRegistry.Endpoint<Object, Void> CONTENT_ENDPOINT =
            REGISTRY.openEndpoint(CONTENT);
    private static long nextRegistrationId;

    private LegacyComposerExtensions() {
    }

    static AutoCloseable register(String pluginId, long generation,
            BooleanSupplier active, ComposerEffect.Factory factory) {
        return register(REGISTRY, pluginId, generation, active, EFFECT,
                () -> PluginComposerRegistry.register(REGISTRY, pluginId, active, factory));
    }

    static AutoCloseable registerText(String pluginId, long generation,
            BooleanSupplier active, ComposerTextEffect.Factory factory) {
        return register(REGISTRY, pluginId, generation, active, TEXT,
                () -> PluginComposerRegistry.registerText(REGISTRY, pluginId, active, factory));
    }

    static AutoCloseable registerCursor(String pluginId, long generation,
            BooleanSupplier active, ComposerCursorEffect.Factory factory) {
        return register(REGISTRY, pluginId, generation, active, CURSOR,
                () -> PluginComposerRegistry.registerCursor(REGISTRY, pluginId, active, factory));
    }

    static AutoCloseable registerSelection(String pluginId, long generation,
            BooleanSupplier active, ComposerSelectionEffect.Factory factory) {
        return register(REGISTRY, pluginId, generation, active, SELECTION,
                () -> PluginComposerRegistry.registerSelection(REGISTRY, pluginId, active, factory));
    }

    static AutoCloseable registerContent(String pluginId, long generation,
            BooleanSupplier active, ComposerContentEffect.Factory factory) {
        return register(REGISTRY, pluginId, generation, active, CONTENT,
                () -> PluginComposerRegistry.registerContent(REGISTRY, pluginId, active, factory));
    }

    private static AutoCloseable register(HostExtensionRegistry registry,
            String pluginId, long generation,
            BooleanSupplier active, HostExtensionDescriptor<Object, Void> descriptor,
            LegacyFactory legacyFactory) {
        return registry.registerObserver(
                pluginId, generation, active, descriptor,
                new HostExtensionRegistrationSpec(nextId(), 0),
                () -> {
                    AutoCloseable legacy = legacyFactory.create();

                    return new HostExtensionObserver<Object>() {
                        @Override
                        public void onObserved(Object input) {
                        }

                        @Override
                        public void close() throws Exception {
                            legacy.close();
                        }
                    };
                });
    }

    private static synchronized String nextId() {
        return "legacy.composer." + nextRegistrationId++;
    }

    private static HostExtensionDescriptor<Object, Void> descriptor(String id) {
        return new HostExtensionDescriptor<>(
                id, 1, HostExtensionDescriptor.Mode.OBSERVER, 2_000_000L, 3, 0);
    }

    private interface LegacyFactory {
        AutoCloseable create();
    }
}
