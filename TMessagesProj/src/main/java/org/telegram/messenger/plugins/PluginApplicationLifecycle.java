package org.telegram.messenger.plugins;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.ForegroundDetector;

public final class PluginApplicationLifecycle implements ForegroundDetector.Listener {
    interface State {
        boolean isBackground();
    }

    interface Scheduler {
        void post(Runnable runnable, long delayMs);

        void cancel(Runnable runnable);
    }

    interface Dispatcher {
        void foregrounded();

        void backgrounded();
    }

    private static final long BACKGROUND_GRACE_MS = 200L;
    private static PluginApplicationLifecycle instance;

    private final State state;
    private final Scheduler scheduler;
    private final Dispatcher dispatcher;
    private boolean foreground;
    private Runnable pendingBackground;

    static synchronized void initialize() {
        if (instance != null) {
            return;
        }

        ForegroundDetector detector = ForegroundDetector.getInstance();

        if (detector == null) {
            return;
        }

        instance = new PluginApplicationLifecycle(
                detector::isBackground,
                new Scheduler() {
                    @Override
                    public void post(Runnable runnable, long delayMs) {
                        AndroidUtilities.runOnUIThread(runnable, delayMs);
                    }

                    @Override
                    public void cancel(Runnable runnable) {
                        AndroidUtilities.cancelRunOnUIThread(runnable);
                    }
                },
                new Dispatcher() {
                    @Override
                    public void foregrounded() {
                        dispatch(true);
                    }

                    @Override
                    public void backgrounded() {
                        dispatch(false);
                    }
                },
                detector.isForeground()
        );
        detector.addListener(instance);
    }

    PluginApplicationLifecycle(
            State state,
            Scheduler scheduler,
            Dispatcher dispatcher,
            boolean foreground) {
        this.state = state;
        this.scheduler = scheduler;
        this.dispatcher = dispatcher;
        this.foreground = foreground;
    }

    @Override
    public synchronized void onBecameForeground() {
        if (pendingBackground != null) {
            scheduler.cancel(pendingBackground);
            pendingBackground = null;
        }

        if (foreground) {
            return;
        }

        foreground = true;
        dispatcher.foregrounded();
    }

    @Override
    public synchronized void onBecameBackground() {
        if (!foreground || pendingBackground != null) {
            return;
        }

        pendingBackground = this::commitBackground;
        scheduler.post(pendingBackground, BACKGROUND_GRACE_MS);
    }

    private synchronized void commitBackground() {
        pendingBackground = null;

        if (!foreground || !state.isBackground()) {
            return;
        }

        foreground = false;
        dispatcher.backgrounded();
    }

    private static void dispatch(boolean foregrounded) {
        int account = UserConfig.selectedAccount;

        if (!UserConfig.isValidAccount(account)) {
            return;
        }

        long ownerId = UserConfig.getInstance(account).getClientUserId();

        if (ownerId <= 0) {
            return;
        }

        PluginApplicationEvent event = PluginApplicationEvent.capture(account, ownerId);

        if (foregrounded) {
            PluginManager.getInstance().applicationForegrounded(event);
        } else {
            PluginManager.getInstance().applicationBackgrounded(event);
        }
    }
}
