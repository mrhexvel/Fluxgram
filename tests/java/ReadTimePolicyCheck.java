import org.telegram.messenger.fluxgram.ReadTimePolicy;

public final class ReadTimePolicyCheck {
    public static void main(String[] args) {
        expect(ReadTimePolicy.shouldCaptureDialog(1, 2, true, false, false, false, false),
                "private user");
        expect(!ReadTimePolicy.shouldCaptureDialog(0, 2, true, false, false, false, false),
                "missing owner");
        expect(!ReadTimePolicy.shouldCaptureDialog(1, 1, true, false, false, false, false),
                "saved messages");
        expect(!ReadTimePolicy.shouldCaptureDialog(1, -2, true, false, false, false, false),
                "group or channel");
        expect(!ReadTimePolicy.shouldCaptureDialog(1, 2, false, false, false, false, false),
                "missing user");
        expect(!ReadTimePolicy.shouldCaptureDialog(1, 2, true, true, false, false, false),
                "bot");
        expect(!ReadTimePolicy.shouldCaptureDialog(1, 2, true, false, true, false, false),
                "service user");
        expect(!ReadTimePolicy.shouldCaptureDialog(1, 2, true, false, false, true, false),
                "reply user");
        expect(!ReadTimePolicy.shouldCaptureDialog(1, 2, true, false, false, false, true),
                "anonymous user");

        expect(ReadTimePolicy.readTime(100, 120) == 120, "observed after send");
        expect(ReadTimePolicy.readTime(120, 100) == 120, "clock cannot predate send");
        expect(ReadTimePolicy.readTime(0, 120) == 0, "missing send time");
        expect(ReadTimePolicy.readTime(100, 0) == 0, "missing observed time");
    }

    private static void expect(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }
}
