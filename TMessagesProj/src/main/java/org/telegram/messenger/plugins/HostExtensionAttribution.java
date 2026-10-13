package org.telegram.messenger.plugins;

import java.util.function.Consumer;

final class HostExtensionAttribution {
    private HostExtensionAttribution() {
    }

    static HostExtensionRegistry create(Consumer<Throwable> report) {
        return new HostExtensionRegistry(
                System::nanoTime,
                new HostExtensionRegistry.CallbackBoundary() {
                    @Override
                    public void run(String pluginId, HostExtensionRegistry.Operation operation)
                            throws Exception {
                        PluginCrashRecovery.runExternal(pluginId, operation::run);
                    }

                    @Override
                    public void handled(Throwable error) {
                        PluginCrashRecovery.handled(error);
                    }
                },
                report);
    }
}
