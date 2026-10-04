package org.telegram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class PluginManager {
    private static final PluginManager INSTANCE = new PluginManager();
    private final LinkedHashMap<String, Entry> plugins = new LinkedHashMap<>();
    private SharedPreferences preferences;
    private volatile boolean systemEnabled;

    private static final class Entry {
        final String id;
        final Plugin plugin;
        volatile boolean enabled = true;
        boolean blockedUntilRestart;

        Entry(String id, Plugin plugin) {
            this.id = id;
            this.plugin = plugin;
        }
    }

    private PluginManager() {}

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(Context context) {
        if (preferences != null) {
            return;
        }

        preferences = context.getSharedPreferences("fluxgram_plugins", Context.MODE_PRIVATE);
        systemEnabled = preferences.getBoolean("system.enabled", false);
    }

    public boolean isSystemEnabled() {
        return systemEnabled;
    }

    public synchronized void setSystemEnabled(boolean enabled) {
        if (systemEnabled == enabled) {
            return;
        }

        systemEnabled = enabled;
        preferences.edit().putBoolean("system.enabled", enabled).apply();

        for (Entry entry : plugins.values()) {
            notifyEnabledChanged(entry);
        }
    }

    private static String enabledKey(String id) {
        return "enabled." + id;
    }

    private void notifyEnabledChanged(Entry entry) {
        try {
            entry.plugin.onEnabledChanged(systemEnabled && entry.enabled);
        } catch (Exception e) {
            FileLog.e("FluxGram plugin state change failed: " + entry.id, e);
        }
    }

    public synchronized void register(Plugin plugin) {
        register(plugin, false);
    }

    public synchronized void register(Plugin plugin, boolean enabledByDefault) {
        if (preferences == null) {
            throw new IllegalStateException("PluginManager is not initialized");
        }

        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }

        String id = plugin.getId();

        if (id == null || id.trim().isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException(
                    "plugin ID must be non-empty and have no surrounding whitespace");
        }

        Entry existing = plugins.get(id);

        if (existing != null) {
            if (existing.plugin == plugin) {
                return;
            }

            throw new IllegalArgumentException("Plugin ID already registered: " + id);
        }

        Entry entry = new Entry(id, plugin);
        entry.enabled = preferences.getBoolean(enabledKey(id), enabledByDefault);
        plugins.put(id, entry);

        notifyEnabledChanged(entry);
    }

    public synchronized boolean setEnabled(String id, boolean enabled) {
        Entry entry = plugins.get(id);

        if (entry == null || (enabled && entry.blockedUntilRestart)) {
            return false;
        }

        if (entry.enabled == enabled) {
            return true;
        }

        entry.enabled = enabled;

        preferences.edit().putBoolean(enabledKey(id), enabled).apply();

        notifyEnabledChanged(entry);

        return true;
    }

    public synchronized Plugin getPlugin(String id) {
        Entry entry = plugins.get(id);

        return entry == null ? null : entry.plugin;
    }

    public synchronized void blockUntilRestart(String id) {
        Entry entry = plugins.get(id);

        if (entry == null) {
            return;
        }

        entry.blockedUntilRestart = true;
        setEnabled(id, false);
    }

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
        notifyEnabledChanged(entry);

        return true;
    }

    public synchronized ArrayList<Plugin> getPlugins() {
        ArrayList<Plugin> result = new ArrayList<>();

        for (Entry entry : plugins.values()) {
            result.add(entry.plugin);
        }

        return result;
    }

    public void beforeSend(BeforeSendEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!systemEnabled || !entry.enabled) {
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
