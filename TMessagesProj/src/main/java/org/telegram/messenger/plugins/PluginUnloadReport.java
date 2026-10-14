package org.telegram.messenger.plugins;

import org.json.JSONException;
import org.json.JSONObject;

public final class PluginUnloadReport {
    public final String pluginId;
    public final long generation;
    public final boolean successful;
    public final String failedPhase;
    public final int openScopes;
    public final int openExternalScopes;
    public final int openResources;
    public final int pendingUiResources;
    public final int managedTasks;
    public final int queuedJobs;
    public final int outstandingEvents;
    public final int outstandingStorage;
    public final boolean javaHostReferencesReleased;
    public final boolean pythonReleased;
    public final boolean sessionReleased;
    public final String classLoaderState;

    PluginUnloadReport(String pluginId, long generation, boolean successful,
            String failedPhase, int openScopes, int openExternalScopes,
            int openResources, int pendingUiResources, int managedTasks,
            int queuedJobs, int outstandingEvents, int outstandingStorage,
            boolean javaHostReferencesReleased, boolean pythonReleased,
            boolean sessionReleased, String classLoaderState) {
        this.pluginId = pluginId;
        this.generation = generation;
        this.successful = successful;
        this.failedPhase = failedPhase;
        this.openScopes = openScopes;
        this.openExternalScopes = openExternalScopes;
        this.openResources = openResources;
        this.pendingUiResources = pendingUiResources;
        this.managedTasks = managedTasks;
        this.queuedJobs = queuedJobs;
        this.outstandingEvents = outstandingEvents;
        this.outstandingStorage = outstandingStorage;
        this.javaHostReferencesReleased = javaHostReferencesReleased;
        this.pythonReleased = pythonReleased;
        this.sessionReleased = sessionReleased;
        this.classLoaderState = classLoaderState;
    }

    public String toJson() {
        try {
            JSONObject resources = new JSONObject();
            resources.put("scopes", openScopes);
            resources.put("external_scopes", openExternalScopes);
            resources.put("handles", openResources);
            resources.put("ui_handles", pendingUiResources);
            resources.put("managed_tasks", managedTasks);
            resources.put("queued_jobs", queuedJobs);
            resources.put("outstanding_events", outstandingEvents);
            resources.put("outstanding_storage", outstandingStorage);

            JSONObject runtime = new JSONObject();
            runtime.put("java_host_references_released", javaHostReferencesReleased);
            runtime.put("python_released", pythonReleased);
            runtime.put("session_released", sessionReleased);
            runtime.put("class_loader_state", classLoaderState);

            JSONObject result = new JSONObject();
            result.put("schema_version", 1);
            result.put("plugin_id", pluginId);
            result.put("generation", generation);
            result.put("successful", successful);
            result.put("failed_phase", failedPhase == null ? JSONObject.NULL : failedPhase);
            result.put("resources", resources);
            result.put("runtime", runtime);
            return result.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Cannot encode plugin unload report", error);
        }
    }
}
