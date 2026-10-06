import org.telegram.messenger.fluxgram.DeletedMessagesScope;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public final class DeletedMessagesScopeCheck {
    public static void main(String[] args) {
        DeletedMessagesScope all = new DeletedMessagesScope(100, true, Collections.emptySet());
        expect(all.allows(100, 1), "user dialog");
        expect(all.allows(100, -2), "chat dialog");
        expect(!all.allows(101, 1), "owner mismatch");
        expect(!all.allows(100, 0), "zero dialog");
        expect(!all.allows(100, 0x2000000000000001L), "folder dialog");
        expect(!all.allows(100, 0x4000000000000001L), "encrypted dialog");

        HashSet<Long> selectedChats = new HashSet<>(Arrays.asList(1L, -2L));
        DeletedMessagesScope selected = new DeletedMessagesScope(100, false, selectedChats);
        selectedChats.clear();

        expect(selected.allows(100, 1), "selected user");
        expect(selected.allows(100, -2), "selected chat");
        expect(!selected.allows(100, 2), "unselected user");
        expect(!new DeletedMessagesScope(0, true, Collections.emptySet()).allows(0, 1), "missing owner");
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }
}
