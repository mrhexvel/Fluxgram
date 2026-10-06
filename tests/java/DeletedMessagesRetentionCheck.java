import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class DeletedMessagesRetentionCheck {
    public static void main(String[] args) {
        Map<Long, HashSet<Integer>> batch = new HashMap<>();
        batch.put(10L, new HashSet<>(Arrays.asList(1, 2)));
        batch.put(20L, new HashSet<>(Arrays.asList(1)));
        batch.put(-30L, new HashSet<>(Arrays.asList(3)));
        batch.put(-40L, new HashSet<>(Arrays.asList(4)));
        batch.put(50L, new HashSet<>());
        DeletedMessagesRetention retained = DeletedMessagesRetention.copyOf(batch);

        expect(retained.forChat(0, false, 10, 0).contains(2));
        expect(!retained.forChat(0, false, 20, 0).contains(2));
        expect(retained.forChat(0, false, 60, 0) == null);
        expect(retained.forChat(0, false, 50, 0) == null);
        expect(retained.forChat(0, false, -30, 0).contains(3));
        expect(retained.forChat(40, true, -40, -30).contains(4));
        expect(retained.forChat(41, true, -40, -30) == null);
        expect(retained.forChat(40, false, 10, 0) == null);
        expect(retained.forChat(0, true, -40, 0) == null);

        DeletedMessagesRetention.Selection migrated = retained.forChat(0, true, -40, -30);
        expect(migrated.dialogId == -30 && migrated.dictionary == 1 && migrated.contains(3));
        expect(!migrated.contains(4));
        expect(retained.forChat(40, true, -40, -30).dictionary == 0);

        batch.get(10L).clear();
        batch.clear();
        expect(retained.forChat(0, false, 10, 0).contains(2));

        DeletedMessagesRetention.Builder builder = new DeletedMessagesRetention.Builder();
        batch.put(10L, new HashSet<>(Arrays.asList(1, 2)));
        builder.add(batch);
        batch.get(10L).clear();
        batch.get(10L).add(3);
        builder.add(batch);
        builder.add(batch);

        int[] deliveries = {0};
        builder.deliverOnUiThread(snapshot -> {
            DeletedMessagesRetention.Selection selection = snapshot.forChat(0, false, 10, 0);
            expect(selection.contains(1) && selection.contains(2) && selection.contains(3));
            expect(!selection.contains(4));
            deliveries[0]++;
        });
        expect(deliveries[0] == 0);

        batch.get(10L).add(4);
        builder.add(batch);
        AndroidUtilities.pending.remove(0).run();
        expect(deliveries[0] == 1);

        new DeletedMessagesRetention.Builder().deliverOnUiThread(snapshot -> {
            expect(snapshot.forChat(0, false, 10, 0) == null);
            deliveries[0]++;
        });
        AndroidUtilities.pending.remove(0).run();
        expect(deliveries[0] == 2 && AndroidUtilities.pending.isEmpty());
    }

    private static void expect(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}

class AndroidUtilities {
    static final ArrayList<Runnable> pending = new ArrayList<>();

    static void runOnUIThread(Runnable runnable) {
        pending.add(runnable);
    }
}
