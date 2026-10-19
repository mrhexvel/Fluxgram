package org.telegram.messenger.plugins;

public final class ExternalPlugins {
    static int changes;

    public static void changed() {
        changes++;
    }
}
