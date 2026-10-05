import org.telegram.messenger.fluxgram.EditHistoryPolicy;

public final class EditHistoryPolicyCheck {
    public static void main(String[] args) {
        byte[] first = new byte[] {1, 2};
        byte[] second = new byte[] {1, 3};

        expect(EditHistoryPolicy.shouldRecord(0, 10, first, second), "first edit");
        expect(EditHistoryPolicy.shouldRecord(10, 11, first, second), "later edit");
        expect(EditHistoryPolicy.shouldRecord(10, 10, first, second), "same second edit");
        expect(!EditHistoryPolicy.shouldRecord(10, 9, first, second), "stale version");
        expect(!EditHistoryPolicy.shouldRecord(0, 0, first, second), "not an edit");
        expect(!EditHistoryPolicy.shouldRecord(10, 11, first, first.clone()), "metadata only");
        expect(EditHistoryPolicy.shouldRecord(10, 11, first, new byte[0]), "cleared caption");
        expect(EditHistoryPolicy.shouldRecord(10, 11, new byte[0], second), "added caption");
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }
}
