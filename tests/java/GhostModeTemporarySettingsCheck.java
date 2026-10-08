final class TestOwners {
    private static final long[] owners = new long[4];

    static long get(int account) {
        return owners[account];
    }

    static void set(int account, long owner) {
        owners[account] = owner;
    }
}

public final class GhostModeTemporarySettingsCheck {
    public static void main(String[] args) {
        TestOwners.set(0, 100);

        check(!GhostModeTemporarySettings.isActive(0), "session starts inactive");

        GhostModeTemporarySettings.Session first = GhostModeTemporarySettings.acquire(0);
        GhostModeTemporarySettings.Session second = GhostModeTemporarySettings.acquire(0);

        check(first != null && second != null, "sessions acquired");
        check(GhostModeTemporarySettings.isActive(0), "session active");

        first.release();
        first.release();
        check(GhostModeTemporarySettings.isActive(0), "second viewer keeps session active");

        second.release();
        check(!GhostModeTemporarySettings.isActive(0), "last viewer releases session");

        GhostModeTemporarySettings.Session oldOwner = GhostModeTemporarySettings.acquire(0);
        TestOwners.set(0, 200);
        check(!GhostModeTemporarySettings.isActive(0), "owner change invalidates session");

        GhostModeTemporarySettings.Session newOwner = GhostModeTemporarySettings.acquire(0);
        oldOwner.release();
        check(GhostModeTemporarySettings.isActive(0), "old token cannot release new owner session");

        GhostModeTemporarySettings.clearAccount(0);
        check(!GhostModeTemporarySettings.isActive(0), "logout clears session");

        newOwner.release();
        TestOwners.set(1, 0);
        check(GhostModeTemporarySettings.acquire(1) == null, "logged out account cannot acquire");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
