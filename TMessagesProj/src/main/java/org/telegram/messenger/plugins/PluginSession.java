package org.telegram.messenger.plugins;

import java.util.function.BooleanSupplier;

final class PluginSession implements AutoCloseable {
    private static final int ROOT_RESOURCE_LIMIT = 1;

    private final String pluginId;
    private final long generation;
    private final BooleanSupplier current;
    private final PluginScope root;

    PluginSession(String pluginId, long generation, BooleanSupplier current,
            PluginResourceRegistry.ThreadGuard threadGuard) {
        this.pluginId = pluginId;
        this.generation = generation;
        this.current = current;
        root = new PluginScope(
                this, null, "plugin:" + pluginId, threadGuard, ROOT_RESOURCE_LIMIT, false);
    }

    String getPluginId() {
        return pluginId;
    }

    long getGeneration() {
        return generation;
    }

    boolean isCurrent() {
        return current.getAsBoolean();
    }

    PluginScope openScope(String name, PluginResourceRegistry.ThreadGuard threadGuard,
            int resourceLimit) {
        return root.openChild(name, threadGuard, resourceLimit);
    }

    PluginScope openExternalScope(String name) {
        return root.openExternalChild(name);
    }

    PluginScope.Diagnostics diagnostics() {
        return root.diagnostics();
    }

    @Override
    public void close() throws Exception {
        root.close();
    }
}
