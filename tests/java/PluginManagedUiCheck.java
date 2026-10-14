package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class PluginManagedUiCheck {
    public static void main(String[] args) throws Exception {
        checkHierarchyIdentityAndRetry();
        checkReentrantCloseRetainsRetry();
        checkBackgroundRecreateAndOwnerChange();
        checkThreadOwnership();
    }

    private static void checkHierarchyIdentityAndRetry() throws Exception {
        AtomicBoolean current = new AtomicBoolean(true);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        List<String> closed = new ArrayList<>();
        PluginManagedUi.Scope activity = PluginManagedUi.openActivity(1, 20, 100, current::get);
        PluginManagedUi.Scope fragment = activity.openFragment(200, () -> true);
        PluginManagedUi.Scope view = fragment.openView(300, () -> true);
        PluginManagedUi.Scope surface = view.openSurface(400, () -> true);

        activity.register(() -> closed.add("activity"));
        fragment.register(() -> closed.add("fragment"));
        view.register(() -> closed.add("view"));
        surface.register(() -> {
            closed.add("surface-retry");

            if (failOnce.getAndSet(false)) {
                throw new Exception("retry");
            }
        });
        surface.register(() -> closed.add("surface"));

        require(surface.getAccount() == 1 && surface.getOwnerId() == 20
                        && activity.getIdentity() == 100 && fragment.getIdentity() == 200
                        && view.getIdentity() == 300 && surface.getIdentity() == 400,
                "managed UI identity was not captured");
        rejects(activity::close);
        require(closed.equals(Arrays.asList(
                        "surface", "surface-retry", "view", "fragment", "activity")),
                "managed UI cleanup order or partial progress mismatch: " + closed);

        activity.close();
        activity.close();
        require(closed.equals(Arrays.asList(
                        "surface", "surface-retry", "view", "fragment", "activity",
                        "surface-retry")),
                "managed UI retry repeated successful cleanup: " + closed);
    }

    private static void checkBackgroundRecreateAndOwnerChange() throws Exception {
        AtomicBoolean currentOwner = new AtomicBoolean(true);
        AtomicBoolean foreground = new AtomicBoolean(true);
        AtomicBoolean visible = new AtomicBoolean(true);
        List<String> closed = new ArrayList<>();
        PluginManagedUi.Scope activity = PluginManagedUi.openActivity(
                2, 30, 500, currentOwner::get);
        PluginManagedUi.Scope fragment = activity.openFragment(600, visible::get);
        PluginManagedUi.Scope firstView = fragment.openView(700, foreground::get);
        firstView.register(() -> closed.add("first"));

        foreground.set(false);
        require(!firstView.isActive(), "background view remained active");
        firstView.close();
        foreground.set(true);
        PluginManagedUi.Scope secondView = fragment.openView(701, foreground::get);
        secondView.register(() -> closed.add("second"));
        require(secondView.isActive(), "recreated foreground view is inactive");

        visible.set(false);
        require(!secondView.isActive(), "hidden fragment retained active view");
        visible.set(true);
        currentOwner.set(false);
        require(!activity.isActive() && !fragment.isActive() && !secondView.isActive(),
                "owner change did not invalidate scope tree");
        activity.close();
        require(closed.equals(Arrays.asList("first", "second")),
                "recreated view cleanup mismatch: " + closed);
    }

    private static void checkReentrantCloseRetainsRetry() throws Exception {
        AtomicBoolean reenter = new AtomicBoolean(true);
        AtomicBoolean cleaned = new AtomicBoolean();
        PluginManagedUi.Scope activity = PluginManagedUi.openActivity(0, 10, 450, () -> true);
        PluginManagedUi.Scope fragment = activity.openFragment(451, () -> true);
        PluginManagedUi.Scope view = fragment.openView(452, () -> true);
        PluginManagedUi.Scope surface = view.openSurface(453, () -> true);

        surface.register(() -> {
            if (reenter.getAndSet(false)) {
                surface.close();
            }

            cleaned.set(true);
        });

        rejects(activity::close);
        require(!cleaned.get(), "reentrant close was treated as successful cleanup");
        activity.close();
        require(cleaned.get(), "reentrant close failure was not retained for retry");
    }

    private static void checkThreadOwnership() throws Exception {
        PluginManagedUi.Scope activity = PluginManagedUi.openActivity(0, 10, 800, () -> true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                activity.isActive();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        thread.start();
        thread.join();
        require(failure.get() instanceof IllegalStateException,
                "managed UI scope accepted a foreign thread");
        activity.close();
    }

    private static void rejects(AutoCloseable action) throws Exception {
        try {
            action.close();
        } catch (Exception expected) {
            return;
        }

        throw new AssertionError("Expected rejection");
    }

    private static void require(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
    }
}
