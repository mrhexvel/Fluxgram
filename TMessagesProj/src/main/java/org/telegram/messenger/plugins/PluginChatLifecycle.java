package org.telegram.messenger.plugins;

import java.util.concurrent.atomic.AtomicLong;

public final class PluginChatLifecycle {
    interface Dispatcher {
        void opened(PluginChatEvent event);

        void closed(PluginChatEvent event);
    }

    private static final AtomicLong nextPresentationId = new AtomicLong(1);

    private final Dispatcher dispatcher;
    private final long presentationId;
    private PluginChatEvent activeEvent;

    public PluginChatLifecycle() {
        this(new Dispatcher() {
            @Override
            public void opened(PluginChatEvent event) {
                PluginManager.getInstance().chatOpened(event);
            }

            @Override
            public void closed(PluginChatEvent event) {
                PluginManager.getInstance().chatClosed(event);
            }
        });
    }

    PluginChatLifecycle(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
        presentationId = nextPresentationId.getAndIncrement();
    }

    public void onVisible(int account, long ownerId, long dialogId, long topicId) {
        if (activeEvent != null
                && activeEvent.account == account
                && activeEvent.ownerId == ownerId
                && activeEvent.dialogId == dialogId
                && activeEvent.topicId == topicId) {
            return;
        }

        closeActive();
        activeEvent = PluginChatEvent.capture(
                account,
                ownerId,
                presentationId,
                dialogId,
                topicId
        );
        dispatcher.opened(activeEvent);
    }

    public void onHidden() {
        closeActive();
    }

    public void onDestroyed() {
        closeActive();
    }

    private void closeActive() {
        if (activeEvent == null) {
            return;
        }

        PluginChatEvent event = activeEvent;
        activeEvent = null;
        dispatcher.closed(event);
    }
}
