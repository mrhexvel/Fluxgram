package org.telegram.messenger.plugins;

import java.util.ArrayList;

class PluginManager {
    private static final PluginManager INSTANCE = new PluginManager();

    static PluginManager getInstance() {
        return INSTANCE;
    }

    void chatOpened(PluginChatEvent event) {}

    void chatClosed(PluginChatEvent event) {}
}

public class PluginChatLifecycleCheck {
    private static void check(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }

    public static void main(String[] args) {
        ArrayList<String> calls = new ArrayList<>();
        ArrayList<PluginChatEvent> events = new ArrayList<>();
        PluginChatLifecycle lifecycle = new PluginChatLifecycle(
                new PluginChatLifecycle.Dispatcher() {
                    @Override
                    public void opened(PluginChatEvent event) {
                        calls.add("open");
                        events.add(event);
                    }

                    @Override
                    public void closed(PluginChatEvent event) {
                        calls.add("close");
                        events.add(event);
                    }
                }
        );

        lifecycle.onVisible(2, 99, -10, 7);
        lifecycle.onVisible(2, 99, -10, 7);
        check(calls.size() == 1 && "open".equals(calls.get(0)));

        long presentationId = events.get(0).presentationId;
        lifecycle.onVisible(2, 99, -10, 8);
        check(calls.size() == 3);
        check("close".equals(calls.get(1)) && "open".equals(calls.get(2)));
        check(events.get(1).topicId == 7 && events.get(2).topicId == 8);
        check(events.get(1).presentationId == presentationId);
        check(events.get(2).presentationId == presentationId);

        lifecycle.onHidden();
        lifecycle.onDestroyed();
        check(calls.size() == 4 && "close".equals(calls.get(3)));

        lifecycle.onVisible(2, 99, Long.MIN_VALUE + 1, Long.MAX_VALUE);
        lifecycle.onDestroyed();
        check(events.get(4).dialogId == Long.MIN_VALUE + 1);
        check(events.get(4).topicId == Long.MAX_VALUE);
        check(events.get(4).presentationId == presentationId);
        check(calls.size() == 6);
    }
}
