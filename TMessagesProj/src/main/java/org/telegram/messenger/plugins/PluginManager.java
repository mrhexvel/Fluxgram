package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class PluginManager {
    private static final PluginManager INSTANCE = new PluginManager();
    private final LinkedHashMap<String, Entry> plugins = new LinkedHashMap<>();

    private static final class Entry {
        final String id;
        final Plugin plugin;
        volatile boolean enabled = true;

        Entry(String id, Plugin plugin) {
            this.id = id;
            this.plugin = plugin;
        }
    }

    private PluginManager() {
    }

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    /** New plugins start enabled. Re-registering the same instance preserves its state. */
    public synchronized void register(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }
        String id = plugin.getId();
        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("plugin ID must be non-empty and have no surrounding whitespace");
        }
        Entry existing = plugins.get(id);
        if (existing != null) {
            if (existing.plugin == plugin) {
                return;
            }
            throw new IllegalArgumentException("Plugin ID already registered: " + id);
        }
        plugins.put(id, new Entry(id, plugin));
    }

    /** Returns false for an unknown ID. Disabling does not interrupt a running callback. */
    public synchronized boolean setEnabled(String id, boolean enabled) {
        Entry entry = plugins.get(id);
        if (entry == null) {
            return false;
        }
        entry.enabled = enabled;
        return true;
    }

    /** Returns false for disabled or unknown plugins. State lasts until process restart. */
    public synchronized boolean isEnabled(String id) {
        Entry entry = plugins.get(id);
        return entry != null && entry.enabled;
    }

    public synchronized boolean unregister(String id) {
        Entry entry = plugins.remove(id);
        if (entry == null) {
            return false;
        }
        entry.enabled = false;
        return true;
    }

    public void beforeSend(BeforeSendEvent event) {
        ArrayList<Entry> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(plugins.values());
        }
        // Call plugin code outside the registry lock, in registration order.
        for (Entry entry : snapshot) {
            if (!entry.enabled) {
                continue;
            }
            try {
                entry.plugin.beforeSend(event);
            } catch (Exception e) {
                FileLog.e("FluxGram before_send plugin failed: " + entry.id, e);
            }
        }
    }
}
