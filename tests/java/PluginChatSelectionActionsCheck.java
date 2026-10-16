package org.telegram.messenger.plugins;

import org.telegram.messenger.R;
import org.telegram.messenger.plugins.api.ChatSelectionAction;
import org.telegram.messenger.plugins.api.ChatSelectionMessageSnapshot;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class PluginChatSelectionActionsCheck {
    public static void main(String[] args) throws Exception {
        AtomicInteger invalidations = new AtomicInteger();
        AtomicInteger selectedA = new AtomicInteger();
        AtomicInteger selectedB = new AtomicInteger();
        AtomicBoolean availableA = new AtomicBoolean(true);
        AtomicBoolean activeA = new AtomicBoolean(true);
        AtomicBoolean activeB = new AtomicBoolean(true);
        PluginChatSelectionActions.Surface surface =
                PluginChatSelectionActions.openSurface(invalidations::incrementAndGet);

        AutoCloseable registrationB = PluginChatSelectionActions.register(
                "plugin.b", 1, activeB::get, "copy_b", "Copy B", "message",
                action(new AtomicBoolean(true), selectedB));
        AutoCloseable registrationA = PluginChatSelectionActions.register(
                "plugin.a", 1, activeA::get, "copy_a", "Copy A", "copy",
                action(availableA, selectedA));
        ChatSelectionMessageSnapshot first = new ChatSelectionMessageSnapshot(42, -100, 7L);
        PluginChatSelectionActions.ActionItem item = surface.findAction(first);

        require(item != null && item.title.equals("Copy A")
                        && item.iconResource == R.drawable.msg_copy,
                "deterministic action ordering or metadata");
        require(surface.perform(item, first) && selectedA.get() == 1 && selectedB.get() == 0,
                "selected action was not isolated");
        require(!surface.perform(item, new ChatSelectionMessageSnapshot(43, -100, 7L)),
                "stale message identity was accepted");

        availableA.set(false);
        require(!surface.perform(item, first) && selectedA.get() == 1,
                "availability was not checked again on click");
        item = surface.findAction(first);
        require(item != null && item.title.equals("Copy B"),
                "unavailable action blocked the next contribution");

        activeB.set(false);
        require(surface.findAction(first) == null, "inactive generation contributed an action");
        activeB.set(true);
        registrationA.close();
        item = surface.findAction(first);
        require(item != null && surface.perform(item, first) && selectedB.get() == 1,
                "remaining registration did not recover after cleanup");
        require(invalidations.get() == 3, "surface invalidation count");

        registrationB.close();
        surface.close();
        require(invalidations.get() == 4, "cleanup invalidation was not delivered");
    }

    private static ChatSelectionAction action(
            AtomicBoolean available, AtomicInteger selected) {
        return new ChatSelectionAction() {
            @Override
            public boolean isAvailable(ChatSelectionMessageSnapshot message) {
                return available.get();
            }

            @Override
            public void onSelected(ChatSelectionMessageSnapshot message) {
                selected.incrementAndGet();
            }
        };
    }

    private static void require(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
    }
}
