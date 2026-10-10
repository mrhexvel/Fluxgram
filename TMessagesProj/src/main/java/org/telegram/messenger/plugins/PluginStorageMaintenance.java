package org.telegram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PluginStorageMaintenance {
    private static final String PREFIX = "owner.";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "FluxGramStorageMaintenance")
    );
    private static SharedPreferences preferences;

    private PluginStorageMaintenance() {
    }

    public static synchronized void initialize(Context context) {
        if (preferences != null) {
            return;
        }

        preferences = context.getApplicationContext().getSharedPreferences(
                "fluxgram_plugin_storage_cleanup",
                Context.MODE_PRIVATE
        );
        runPendingOwnerCleanup();
    }

    public static synchronized boolean scheduleOwnerCleanup(int account, long owner) {
        if (preferences == null || account < 0 || owner <= 0) {
            return false;
        }

        return preferences.edit().putBoolean(key(account, owner), true).commit();
    }

    public static synchronized void runPendingOwnerCleanup() {
        if (preferences == null) {
            return;
        }

        worker.execute(PluginStorageMaintenance::drain);
    }

    private static void drain() {
        Map<String, ?> pending;

        synchronized (PluginStorageMaintenance.class) {
            pending = preferences.getAll();
        }

        for (String key : pending.keySet()) {
            Owner owner = parse(key);

            if (owner == null) {
                continue;
            }

            try {
                if (!PluginStorage.clearOwner(owner.account, owner.owner)) {
                    continue;
                }

                synchronized (PluginStorageMaintenance.class) {
                    if (!preferences.edit().remove(key).commit()) {
                        throw new IllegalStateException("Cannot finish plugin owner cleanup");
                    }
                }
            } catch (Exception error) {
                FileLog.e(error);
            }
        }
    }

    private static String key(int account, long owner) {
        return PREFIX + account + "." + owner;
    }

    private static Owner parse(String key) {
        if (!key.startsWith(PREFIX)) {
            return null;
        }

        String[] fields = key.substring(PREFIX.length()).split("\\.", -1);

        if (fields.length != 2) {
            return null;
        }

        try {
            int account = Integer.parseInt(fields[0]);
            long owner = Long.parseLong(fields[1]);

            if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || owner <= 0) {
                return null;
            }

            return new Owner(account, owner);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static final class Owner {
        final int account;
        final long owner;

        Owner(int account, long owner) {
            this.account = account;
            this.owner = owner;
        }
    }
}
