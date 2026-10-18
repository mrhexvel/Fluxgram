package org.telegram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;

public final class PluginManager {
    private static final long TRANSFORM_TOTAL_TIMEOUT_MS = 1000L;
    private static final long TRANSFORM_PLUGIN_TIMEOUT_MS = 400L;
    private static final PluginManager INSTANCE = new PluginManager();
    private final LinkedHashMap<String, Entry> plugins = new LinkedHashMap<>();
    private SharedPreferences preferences;
    private volatile boolean systemEnabled;
    private volatile boolean recoveryLatched;
    private volatile boolean recoveryRestartRequired;

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
        recoveryLatched = PluginCrashRecovery.consumePending(preferences);
        systemEnabled = !recoveryLatched && preferences.getBoolean("system.enabled", false);
        PluginMessageEvents.initialize();
        PluginApplicationLifecycle.initialize();
    }

    public boolean isSystemEnabled() {
        return systemEnabled;
    }

    public synchronized void setSystemEnabled(boolean enabled) {
        if (enabled && recoveryLatched) {
            boolean saved = preferences.edit()
                    .putBoolean("system.enabled", true)
                    .remove(PluginCrashRecovery.LATCHED_KEY)
                    .commit();

            if (!saved) {
                return;
            }

            systemEnabled = true;
            recoveryLatched = false;
            recoveryRestartRequired = true;

            return;
        }

        if (systemEnabled == enabled) {
            return;
        }

        systemEnabled = enabled;
        preferences.edit().putBoolean("system.enabled", enabled).apply();

        if (!enabled) {
            PluginDeveloperBridge.stopSession();
        }

        for (Entry entry : plugins.values()) {
            notifyEnabledChanged(entry);
        }
    }

    private static String enabledKey(String id) {
        return "enabled." + id;
    }

    private void notifyEnabledChanged(Entry entry) {
        try {
            entry.plugin.onEnabledChanged(isRuntimeEnabled() && entry.enabled);
        } catch (Exception e) {
            FileLog.e("FluxGram plugin state change failed: " + entry.id, e);
        }
    }

    public boolean isRecoveryLatched() {
        return recoveryLatched;
    }

    public boolean isRecoveryRestartRequired() {
        return recoveryRestartRequired;
    }

    public boolean isRecoveryBlockingImports() {
        return recoveryLatched || recoveryRestartRequired;
    }

    public String getRecoveryPluginId() {
        return isRecoveryBlockingImports()
                ? preferences.getString(PluginCrashRecovery.PLUGIN_KEY, null) : null;
    }

    public boolean isRecoveryPlugin(String id) {
        String recoveryPluginId = getRecoveryPluginId();

        return recoveryPluginId != null && recoveryPluginId.equals(id);
    }

    public boolean hasRecoveryNotice() {
        return recoveryLatched
                && preferences.getBoolean(PluginCrashRecovery.NOTICE_KEY, false);
    }

    public synchronized boolean claimRecoveryNotice() {
        if (!hasRecoveryNotice()) {
            return false;
        }

        return preferences.edit().remove(PluginCrashRecovery.NOTICE_KEY).commit();
    }

    private boolean isRuntimeEnabled() {
        return systemEnabled && !isRecoveryBlockingImports();
    }

    public synchronized void register(Plugin plugin) {
        register(plugin, false);
    }

    public synchronized void register(Plugin plugin, boolean enabledByDefault) {
        register(plugin, enabledByDefault, false);
    }

    synchronized void registerBlockedUntilRestart(Plugin plugin) {
        register(plugin, false, true);
    }

    private void register(Plugin plugin, boolean enabledByDefault, boolean blockedUntilRestart) {
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
        entry.enabled = !blockedUntilRestart
                && preferences.getBoolean(enabledKey(id), enabledByDefault);
        entry.blockedUntilRestart = blockedUntilRestart;
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

    synchronized boolean unregisterStopped(String id, Plugin plugin) {
        Entry entry = plugins.get(id);

        if (entry == null || entry.plugin != plugin) {
            return false;
        }

        plugins.remove(id);
        return true;
    }

    public synchronized void clearPersistedEnabledState(String id) {
        preferences.edit().remove(enabledKey(id)).apply();
    }

    public synchronized boolean clearPersistedEnabledStateNow(String id) {
        return preferences.edit().remove(enabledKey(id)).commit();
    }

    public synchronized boolean setPersistedEnabledState(String id, boolean enabled) {
        return preferences.edit().putBoolean(enabledKey(id), enabled).commit();
    }

    public synchronized ArrayList<Plugin> getPlugins() {
        ArrayList<Plugin> result = new ArrayList<>();

        for (Entry entry : plugins.values()) {
            result.add(entry.plugin);
        }

        return result;
    }

    synchronized boolean hasMessageReceivedPlugins() {
        if (!isRuntimeEnabled()) {
            return false;
        }

        for (Entry entry : plugins.values()) {
            if (entry.enabled && entry.plugin.hasMessageReceivedSubscribers()) {
                return true;
            }
        }

        return false;
    }

    void messageReceived(PluginMessageReceivedEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled()
                    || !entry.enabled
                    || !entry.plugin.hasMessageReceivedSubscribers()) {
                continue;
            }

            try {
                entry.plugin.messageReceived(event);
            } catch (Exception e) {
                FileLog.e("FluxGram message_received plugin failed: " + entry.id, e);
            }
        }
    }

    synchronized boolean hasMessageEditedPlugins() {
        if (!isRuntimeEnabled()) {
            return false;
        }

        for (Entry entry : plugins.values()) {
            if (entry.enabled && entry.plugin.hasMessageEditedSubscribers()) {
                return true;
            }
        }

        return false;
    }

    void messageEdited(PluginMessageEditedEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled()
                    || !entry.enabled
                    || !entry.plugin.hasMessageEditedSubscribers()) {
                continue;
            }

            try {
                entry.plugin.messageEdited(event);
            } catch (Exception e) {
                FileLog.e("FluxGram message_edited plugin failed: " + entry.id, e);
            }
        }
    }

    synchronized boolean hasMessageDeletedPlugins() {
        if (!isRuntimeEnabled()) {
            return false;
        }

        for (Entry entry : plugins.values()) {
            if (entry.enabled && entry.plugin.hasMessageDeletedSubscribers()) {
                return true;
            }
        }

        return false;
    }

    void messagesDeleted(PluginMessagesDeletedEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled()
                    || !entry.enabled
                    || !entry.plugin.hasMessageDeletedSubscribers()) {
                continue;
            }

            try {
                entry.plugin.messagesDeleted(event);
            } catch (Exception e) {
                FileLog.e("FluxGram message_deleted plugin failed: " + entry.id, e);
            }
        }
    }

    synchronized boolean hasChatOpenedPlugins() {
        if (!isRuntimeEnabled()) {
            return false;
        }

        for (Entry entry : plugins.values()) {
            if (entry.enabled && entry.plugin.hasChatOpenedSubscribers()) {
                return true;
            }
        }

        return false;
    }

    void chatOpened(PluginChatEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            if (!hasChatOpenedPlugins()) {
                return;
            }

            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled()
                    || !entry.enabled
                    || !entry.plugin.hasChatOpenedSubscribers()) {
                continue;
            }

            try {
                entry.plugin.chatOpened(event);
            } catch (Exception e) {
                FileLog.e("FluxGram chat_opened plugin failed: " + entry.id, e);
            }
        }
    }

    synchronized boolean hasChatClosedPlugins() {
        if (!isRuntimeEnabled()) {
            return false;
        }

        for (Entry entry : plugins.values()) {
            if (entry.enabled && entry.plugin.hasChatClosedSubscribers()) {
                return true;
            }
        }

        return false;
    }

    void chatClosed(PluginChatEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            if (!hasChatClosedPlugins()) {
                return;
            }

            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled()
                    || !entry.enabled
                    || !entry.plugin.hasChatClosedSubscribers()) {
                continue;
            }

            try {
                entry.plugin.chatClosed(event);
            } catch (Exception e) {
                FileLog.e("FluxGram chat_closed plugin failed: " + entry.id, e);
            }
        }
    }

    synchronized boolean hasApplicationForegroundedPlugins() {
        if (!isRuntimeEnabled()) {
            return false;
        }

        for (Entry entry : plugins.values()) {
            if (entry.enabled && entry.plugin.hasApplicationForegroundedSubscribers()) {
                return true;
            }
        }

        return false;
    }

    void applicationForegrounded(PluginApplicationEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            if (!hasApplicationForegroundedPlugins()) {
                return;
            }

            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled()
                    || !entry.enabled
                    || !entry.plugin.hasApplicationForegroundedSubscribers()) {
                continue;
            }

            try {
                entry.plugin.applicationForegrounded(event);
            } catch (Exception e) {
                FileLog.e("FluxGram application_foregrounded plugin failed: " + entry.id, e);
            }
        }
    }

    synchronized boolean hasApplicationBackgroundedPlugins() {
        if (!isRuntimeEnabled()) {
            return false;
        }

        for (Entry entry : plugins.values()) {
            if (entry.enabled && entry.plugin.hasApplicationBackgroundedSubscribers()) {
                return true;
            }
        }

        return false;
    }

    void applicationBackgrounded(PluginApplicationEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            if (!hasApplicationBackgroundedPlugins()) {
                return;
            }

            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled()
                    || !entry.enabled
                    || !entry.plugin.hasApplicationBackgroundedSubscribers()) {
                continue;
            }

            try {
                entry.plugin.applicationBackgrounded(event);
            } catch (Exception e) {
                FileLog.e("FluxGram application_backgrounded plugin failed: " + entry.id, e);
            }
        }
    }

    public void beforeSend(BeforeSendEvent event) {
        ArrayList<Entry> snapshot;

        synchronized (this) {
            snapshot = new ArrayList<>(plugins.values());
        }

        for (Entry entry : snapshot) {
            if (!isRuntimeEnabled() || !entry.enabled) {
                continue;
            }

            try {
                entry.plugin.beforeSend(event);
            } catch (Exception e) {
                FileLog.e("FluxGram before_send plugin failed: " + entry.id, e);
            }
        }
    }

    public boolean transformOutgoingText(
            OutgoingTextTransformRequest request,
            OutgoingTextTransformCallback callback) {
        ArrayList<Entry> snapshot = new ArrayList<>();

        if (request == null || callback == null || !request.isValid()) {
            return false;
        }

        synchronized (this) {
            if (!isRuntimeEnabled()) {
                return false;
            }

            for (Entry entry : plugins.values()) {
                if (entry.enabled && entry.plugin.hasOutgoingTextTransformer()) {
                    snapshot.add(entry);
                }
            }
        }

        if (snapshot.isEmpty()) {
            return false;
        }

        snapshot.sort(Comparator.comparing(entry -> entry.id));
        new OutgoingTextPipeline(snapshot, request, callback).advance();

        return true;
    }

    private synchronized boolean canTransform(Entry entry) {
        return isRuntimeEnabled()
                && entry.enabled
                && entry.plugin.hasOutgoingTextTransformer();
    }

    private final class OutgoingTextPipeline {
        private final ArrayList<Entry> entries;
        private final OutgoingTextTransformCallback callback;
        private final long deadline;
        private OutgoingTextTransformRequest request;
        private int index;
        private int stage;
        private boolean completed;
        private Runnable timeout;

        OutgoingTextPipeline(
                ArrayList<Entry> entries,
                OutgoingTextTransformRequest request,
                OutgoingTextTransformCallback callback) {
            this.entries = entries;
            this.request = request;
            this.callback = callback;
            deadline = System.currentTimeMillis() + TRANSFORM_TOTAL_TIMEOUT_MS;
        }

        void advance() {
            if (completed) {
                return;
            }

            long remaining = deadline - System.currentTimeMillis();

            if (index >= entries.size() || remaining <= 0) {
                finish();

                return;
            }

            Entry entry = entries.get(index++);

            if (!canTransform(entry)) {
                advance();

                return;
            }

            int currentStage = ++stage;
            timeout = () -> accept(currentStage, null);
            AndroidUtilities.runOnUIThread(
                    timeout,
                    Math.min(TRANSFORM_PLUGIN_TIMEOUT_MS, remaining)
            );

            try {
                entry.plugin.transformOutgoingText(
                        request,
                        text -> AndroidUtilities.runOnUIThread(
                                () -> accept(currentStage, text)
                        )
                );
            } catch (Exception error) {
                FileLog.e("FluxGram outgoing text transform failed: " + entry.id, error);
                accept(currentStage, null);
            }
        }

        void accept(int currentStage, String text) {
            if (completed || currentStage != stage) {
                return;
            }

            if (timeout != null) {
                AndroidUtilities.cancelRunOnUIThread(timeout);
                timeout = null;
            }

            if (text != null && text.length() <= OutgoingTextTransformRequest.MAX_TEXT_LENGTH) {
                request = request.withText(text);
            }

            advance();
        }

        void finish() {
            if (completed) {
                return;
            }

            completed = true;

            if (timeout != null) {
                AndroidUtilities.cancelRunOnUIThread(timeout);
                timeout = null;
            }

            callback.complete(request.text);
        }
    }
}
