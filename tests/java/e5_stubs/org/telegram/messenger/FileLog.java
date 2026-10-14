package org.telegram.messenger;

public final class FileLog {
    public static int errors;

    private FileLog() {
    }

    public static void e(Throwable error) {
        errors++;
    }
}
