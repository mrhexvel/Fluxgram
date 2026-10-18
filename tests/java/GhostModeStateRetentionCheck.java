import java.util.HashMap;
import java.util.Map;

public final class GhostModeStateRetentionCheck {
    public static void main(String[] args) {
        Map<String, Object> values = new HashMap<>();
        values.put("content_1", true);
        values.put(GhostModeStateRetention.orderKey("content_1"), 2L);
        values.put("content_2", true);
        values.put(GhostModeStateRetention.orderKey("content_2"), 1L);

        check(GhostModeStateRetention.evictionCandidate(
                values, 3, null, "content_") == null, "under limit");

        values.put("content_3", true);
        values.put(GhostModeStateRetention.orderKey("content_3"), 3L);
        check("content_2".equals(GhostModeStateRetention.evictionCandidate(
                values, 3, null, "content_")), "oldest state");

        values.put("content_0", true);
        check("content_0".equals(GhostModeStateRetention.evictionCandidate(
                values, 3, null, "content_")), "legacy state");
        check(GhostModeStateRetention.excessEntries(
                values, 2, null, "content_").size() == 2, "legacy excess");
        check("content_0".equals(GhostModeStateRetention.excessEntries(
                values, 2, null, "content_").get(0)), "legacy first");

        Map<String, Object> cursors = new HashMap<>();
        cursors.put("dialog_1", 5);
        cursors.put("dialog_1_hidden_unread", 2);
        cursors.put(GhostModeStateRetention.orderKey("dialog_1"), 1L);
        check(GhostModeStateRetention.evictionCandidate(
                cursors, 2, "_hidden_unread", "dialog_", "topic_") == null,
                "cursor companion excluded");

        cursors.put("topic_1_2", 7);
        cursors.put(GhostModeStateRetention.orderKey("topic_1_2"), 2L);
        check("dialog_1".equals(GhostModeStateRetention.evictionCandidate(
                cursors, 2, "_hidden_unread", "dialog_", "topic_")),
                "shared cursor limit");

        check(GhostModeStateRetention.nextOrder(4) == 5, "next order");
        check(GhostModeStateRetention.nextOrder(Long.MAX_VALUE) == Long.MAX_VALUE, "order overflow");
    }

    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
