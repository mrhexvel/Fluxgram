package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.function.Consumer;

final class HostExtensionAttribution {
    static int handled;
    static final ArrayList<String> pluginIds = new ArrayList<>();

    private HostExtensionAttribution() {
    }

    static HostExtensionRegistry create(Consumer<Throwable> report) {
        return new HostExtensionRegistry(
                System::nanoTime,
                new HostExtensionRegistry.CallbackBoundary() {
                    @Override
                    public void run(String pluginId, HostExtensionRegistry.Operation operation)
                            throws Exception {
                        pluginIds.add(pluginId);
                        operation.run();
                    }

                    @Override
                    public void handled(Throwable error) {
                        handled++;
                    }
                },
                report);
    }
}
