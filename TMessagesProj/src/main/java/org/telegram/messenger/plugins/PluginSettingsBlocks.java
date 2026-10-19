package org.telegram.messenger.plugins;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.fluxgram.JavaSettingsBlock;
import org.fluxgram.JavaSettingsBlockContext;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class PluginSettingsBlocks {
    private static final int REGISTRATION_LIMIT = 32;
    private static final int SCREEN_LIMIT = 4;

    interface ActionDispatcher {
        boolean post(long generation, BooleanSupplier active, Runnable action);
    }

    private final String pluginId;
    private final long generation;
    private final BooleanSupplier generationActive;
    private final ActionDispatcher actionDispatcher;
    private final ArrayList<Registration> registrations = new ArrayList<>();
    private final HashSet<String> ids = new HashSet<>();

    PluginSettingsBlocks(String pluginId, long generation,
                         BooleanSupplier generationActive, ActionDispatcher actionDispatcher) {
        this.pluginId = pluginId;
        this.generation = generation;
        this.generationActive = generationActive;
        this.actionDispatcher = actionDispatcher;
    }

    AutoCloseable register(String id, String label, JavaSettingsBlock.Factory factory) {
        requireUiThread();

        if (!generationActive.getAsBoolean()) {
            throw new IllegalStateException("Settings block generation is inactive");
        }

        if (registrations.size() >= REGISTRATION_LIMIT) {
            throw new IllegalStateException("Settings block registration limit reached");
        }

        if (!ids.add(id)) {
            throw new IllegalArgumentException("Duplicate settings block ID");
        }

        Registration registration = new Registration(id, label, factory);
        registrations.add(registration);
        ExternalPlugins.changed();
        return registration;
    }

    public List<Registration> snapshot() {
        requireUiThread();
        ArrayList<Registration> active = new ArrayList<>();

        for (Registration registration : registrations) {
            if (registration.active) {
                active.add(registration);
            }
        }

        return Collections.unmodifiableList(active);
    }

    private static void requireUiThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Settings blocks require main thread");
        }
    }

    public final class Registration implements AutoCloseable {
        private final String id;
        private final String label;
        private final JavaSettingsBlock.Factory factory;
        private final ArrayList<Binding> bindings = new ArrayList<>();
        private volatile boolean active = true;
        private boolean closing;

        private Registration(String id, String label, JavaSettingsBlock.Factory factory) {
            this.id = id;
            this.label = label;
            this.factory = factory;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public Binding bind(
                Context context,
                Theme.ResourcesProvider resourcesProvider,
                BooleanSupplier screenActive) throws Exception {
            requireUiThread();

            if (!active || closing || !generationActive.getAsBoolean()
                    || !screenActive.getAsBoolean()) {
                throw new IllegalStateException("Settings block registration is inactive");
            }

            if (bindings.size() >= SCREEN_LIMIT) {
                throw new IllegalStateException("Settings block screen limit reached");
            }

            for (Binding binding : bindings) {
                if (!binding.closed && binding.cleanupFailed) {
                    throw new IllegalStateException("Settings block cleanup is pending");
                }
            }

            AtomicBoolean bindingActive = new AtomicBoolean(true);
            BlockContext blockContext = new BlockContext(
                    context,
                    resourcesProvider,
                    () -> bindingActive.get() && active && generationActive.getAsBoolean()
                            && screenActive.getAsBoolean());
            JavaSettingsBlock[] created = new JavaSettingsBlock[1];
            Binding[] binding = new Binding[1];
            View[] root = new View[1];

            try {
                PluginCrashRecovery.runExternal(pluginId, () -> created[0] = factory.create(blockContext));

                if (created[0] == null) {
                    throw new IllegalStateException("Settings block factory returned no view");
                }

                binding[0] = new Binding(created[0], bindingActive, blockContext::isActive);
                bindings.add(binding[0]);

                PluginCrashRecovery.runExternal(pluginId, () -> root[0] = created[0].getView());

                if (root[0] == null) {
                    throw new IllegalStateException("Settings block factory returned no view");
                }

                if (root[0].getParent() != null) {
                    throw new IllegalStateException("Settings block view must be unparented");
                }

                binding[0].setView(root[0]);
                return binding[0];
            } catch (Exception | LinkageError | AssertionError error) {
                PluginCrashRecovery.handled(error);
                bindingActive.set(false);

                if (binding[0] != null) {
                    try {
                        binding[0].close();
                    } catch (Exception | LinkageError | AssertionError cleanupError) {
                        error.addSuppressed(cleanupError);
                    }
                }

                throw error;
            }
        }

        @Override
        public void close() throws Exception {
            requireUiThread();

            if (!closing) {
                closing = true;
                active = false;
                ExternalPlugins.changed();
            }

            Exception failure = null;
            ArrayList<Binding> snapshot = new ArrayList<>(bindings);
            Collections.reverse(snapshot);

            for (Binding binding : snapshot) {
                try {
                    binding.close();
                } catch (Exception | LinkageError | AssertionError error) {
                    if (failure == null) {
                        failure = new Exception("Settings block cleanup failed", error);
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }

            if (failure != null) {
                throw failure;
            }

            registrations.remove(this);
            ids.remove(id);
        }

        public final class Binding implements AutoCloseable {
            private final JavaSettingsBlock block;
            private final AtomicBoolean bindingActive;
            private final BooleanSupplier active;
            private View view;
            private boolean cleanupFailed;
            private boolean closed;

            private Binding(JavaSettingsBlock block, AtomicBoolean bindingActive,
                            BooleanSupplier active) {
                this.block = block;
                this.bindingActive = bindingActive;
                this.active = active;
            }

            private void setView(View view) {
                this.view = view;
            }

            public View getView() {
                if (view == null) {
                    throw new IllegalStateException("Settings block view is unavailable");
                }

                return view;
            }

            public boolean isActive() {
                return active.getAsBoolean() && !cleanupFailed && !closed;
            }

            @Override
            public void close() throws Exception {
                requireUiThread();

                if (closed) {
                    return;
                }

                bindingActive.set(false);
                ViewParent parent = view == null ? null : view.getParent();

                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(view);
                }

                try {
                    PluginCrashRecovery.runExternal(pluginId, block::close);
                    cleanupFailed = false;
                    closed = true;
                    bindings.remove(this);
                } catch (Exception | LinkageError | AssertionError error) {
                    cleanupFailed = true;
                    PluginCrashRecovery.handled(error);
                    throw error;
                }
            }
        }
    }

    private final class BlockContext implements JavaSettingsBlockContext {
        private final Context context;
        private final Theme.ResourcesProvider resourcesProvider;
        private final BooleanSupplier active;

        private BlockContext(
                Context context,
                Theme.ResourcesProvider resourcesProvider,
                BooleanSupplier active) {
            this.context = context;
            this.resourcesProvider = resourcesProvider;
            this.active = active;
        }

        @Override
        public Context getContext() {
            requireUiThread();

            if (!isActive()) {
                throw new IllegalStateException("Settings block is inactive");
            }

            return context;
        }

        @Override
        public int getColor(int role) {
            requireUiThread();

            if (!isActive()) {
                throw new IllegalStateException("Settings block is inactive");
            }

            switch (role) {
                case COLOR_BACKGROUND:
                    return Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
                case COLOR_PRIMARY_TEXT:
                    return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
                case COLOR_SECONDARY_TEXT:
                    return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
                case COLOR_ACCENT:
                    return Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider);
                case COLOR_DIVIDER:
                    return Theme.getColor(Theme.key_divider, resourcesProvider);
                default:
                    throw new IllegalArgumentException("Unknown settings block color role");
            }
        }

        @Override
        public boolean isActive() {
            return active.getAsBoolean();
        }

        @Override
        public boolean postAction(Runnable action) {
            if (action == null) {
                throw new IllegalArgumentException("Settings block action is required");
            }

            return isActive() && actionDispatcher.post(generation, this::isActive, action);
        }
    }
}
