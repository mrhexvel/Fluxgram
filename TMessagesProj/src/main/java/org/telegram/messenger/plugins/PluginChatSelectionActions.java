package org.telegram.messenger.plugins;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.ChatSelectionAction;
import org.telegram.messenger.plugins.api.ChatSelectionMessageSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class PluginChatSelectionActions {
    private static final HostExtensionDescriptor<Request, ActionItem> DESCRIPTOR =
            new HostExtensionDescriptor<>(
                    "ui.chat.message.selection_action",
                    1,
                    HostExtensionDescriptor.Mode.CONTRIBUTOR,
                    4_000_000L,
                    3,
                    1
            );
    private static final HostExtensionRegistry REGISTRY =
            HostExtensionAttribution.create(FileLog::e);
    private static final ArrayList<Runnable> invalidationListeners = new ArrayList<>();

    private PluginChatSelectionActions() {
    }

    static AutoCloseable register(String pluginId, long generation,
            BooleanSupplier active, String id, String title, String icon,
            ChatSelectionAction action) {
        requireText(id, 128, "action ID");
        requireText(title, 64, "action title");

        if (!id.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid action ID");
        }

        if (action == null) {
            throw new IllegalArgumentException("Chat selection action is required");
        }

        int iconResource = PluginSemanticIcons.resolve(icon);
        String key = pluginId + "\n" + id;
        AutoCloseable registration = REGISTRY.registerContributor(
                pluginId,
                generation,
                active,
                DESCRIPTOR,
                new HostExtensionRegistrationSpec(id, 0),
                () -> (request, sink) -> {
                    if (request.targetKey != null && !request.targetKey.equals(key)) {
                        return;
                    }

                    if (!action.isAvailable(request.message)) {
                        return;
                    }

                    if (request.targetKey != null) {
                        action.onSelected(request.message);
                    }

                    sink.add(new ActionItem(
                            key,
                            title,
                            iconResource,
                            request.message.messageId,
                            request.message.dialogId
                    ));
                }
        );
        invalidateSurfaces();

        return () -> {
            try {
                registration.close();
            } finally {
                invalidateSurfaces();
            }
        };
    }

    public static Surface openSurface(Runnable invalidationListener) {
        if (invalidationListener == null) {
            throw new IllegalArgumentException("Invalidation listener is required");
        }

        HostExtensionRegistry.Endpoint<Request, ActionItem> endpoint =
                REGISTRY.openEndpoint(DESCRIPTOR);

        synchronized (invalidationListeners) {
            invalidationListeners.add(invalidationListener);
        }

        return new Surface(endpoint, invalidationListener);
    }

    private static void requireText(String value, int maximumLength, String name) {
        if (value == null || value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private static void invalidateSurfaces() {
        List<Runnable> listeners;

        synchronized (invalidationListeners) {
            listeners = new ArrayList<>(invalidationListeners);
        }

        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    public static final class Surface implements AutoCloseable {
        private HostExtensionRegistry.Endpoint<Request, ActionItem> endpoint;
        private Runnable invalidationListener;

        private Surface(HostExtensionRegistry.Endpoint<Request, ActionItem> endpoint,
                Runnable invalidationListener) {
            this.endpoint = endpoint;
            this.invalidationListener = invalidationListener;
        }

        public ActionItem findAction(ChatSelectionMessageSnapshot message) {
            HostExtensionRegistry.Endpoint<Request, ActionItem> current = endpoint;

            if (current == null || message == null) {
                return null;
            }

            List<ActionItem> actions = current.collectContributions(new Request(message, null));
            return actions.isEmpty() ? null : actions.get(0);
        }

        public boolean perform(ActionItem item, ChatSelectionMessageSnapshot message) {
            HostExtensionRegistry.Endpoint<Request, ActionItem> current = endpoint;

            if (current == null || item == null || !item.matches(message)) {
                return false;
            }

            List<ActionItem> handled = current.collectContributions(new Request(message, item.key));
            return !handled.isEmpty();
        }

        @Override
        public void close() throws Exception {
            HostExtensionRegistry.Endpoint<Request, ActionItem> closing = endpoint;
            Runnable listener = invalidationListener;
            endpoint = null;
            invalidationListener = null;

            if (listener != null) {
                synchronized (invalidationListeners) {
                    invalidationListeners.remove(listener);
                }
            }

            if (closing != null) {
                closing.close();
            }
        }
    }

    public static final class ActionItem {
        private final String key;
        public final String title;
        public final int iconResource;
        private final int messageId;
        private final long dialogId;

        private ActionItem(String key, String title, int iconResource,
                int messageId, long dialogId) {
            this.key = key;
            this.title = title;
            this.iconResource = iconResource;
            this.messageId = messageId;
            this.dialogId = dialogId;
        }

        public boolean matches(ChatSelectionMessageSnapshot message) {
            return message != null
                    && messageId == message.messageId
                    && dialogId == message.dialogId;
        }
    }

    private static final class Request {
        final ChatSelectionMessageSnapshot message;
        final String targetKey;

        Request(ChatSelectionMessageSnapshot message, String targetKey) {
            this.message = message;
            this.targetKey = targetKey;
        }
    }
}
