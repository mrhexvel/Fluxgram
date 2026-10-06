public class PluginUiLifecycleCheck {

    public static void main(String[] args) {
        int[] calls = new int[2];
        long[] identity = new long[3];
        PluginUiLifecycle.Listener failing = new PluginUiLifecycle.Listener() {
            @Override
            public void onBackground() {
                PluginUiLifecycle.removeListener(this);
                throw new IllegalStateException("listener failure");
            }

            @Override
            public void onChatOpened(int account, long dialogId, long topicId) {
                throw new LinkageError("unloaded plugin");
            }
        };
        PluginUiLifecycle.Listener listener = new PluginUiLifecycle.Listener() {
            @Override
            public void onBackground() {
                calls[0]++;
            }

            @Override
            public void onChatOpened(int account, long dialogId, long topicId) {
                calls[1]++;
                identity[0] = account;
                identity[1] = dialogId;
                identity[2] = topicId;
            }
        };

        PluginUiLifecycle.addListener(failing);
        PluginUiLifecycle.addListener(listener);
        PluginUiLifecycle.addListener(listener);
        PluginUiLifecycle.onChatOpened(2, -10000000001L, 8000000001L);
        PluginUiLifecycle.onBackground();

        if (calls[0] != 1 || calls[1] != 1 || FileLog.errors != 2) {
            throw new AssertionError("Listener failure or duplicate disrupted delivery");
        }

        if (identity[0] != 2 || identity[1] != -10000000001L || identity[2] != 8000000001L) {
            throw new AssertionError("Chat identity was truncated");
        }

        PluginUiLifecycle.removeListener(listener);
        PluginUiLifecycle.onBackground();
        PluginUiLifecycle.onChatOpened(0, 1, 0);

        if (calls[0] != 1 || calls[1] != 1 || FileLog.errors != 2) {
            throw new AssertionError("Removed listeners still receive events");
        }
    }
}

class FileLog {
    static int errors;

    static void e(Throwable error) {
        errors++;
    }
}
