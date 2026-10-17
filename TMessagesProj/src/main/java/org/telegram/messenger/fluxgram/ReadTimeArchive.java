package org.telegram.messenger.fluxgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.function.Consumer;

public final class ReadTimeArchive {
    private static final String OWNER = "owner";
    private static final int MAX_RECORDS = 100000;
    private static final int TRIM_INTERVAL = 256;
    private static final SparseIntArray pendingTrim = new SparseIntArray();
    private static final SparseBooleanArray trimInitialized = new SparseBooleanArray();

    private ReadTimeArchive() {
    }

    public static final class Entry {
        public final int readAt;
        public final boolean exact;

        private Entry(int readAt, boolean exact) {
            this.readAt = readAt;
            this.exact = exact;
        }
    }

    public static synchronized boolean isEnabled(int account) {
        SharedPreferences preferences = activePreferences(account);

        return preferences != null && preferences.getBoolean("enabled", false);
    }

    public static synchronized void setEnabled(int account, boolean enabled) {
        SharedPreferences preferences = activePreferences(account);

        if (preferences != null) {
            preferences.edit().putBoolean("enabled", enabled).apply();
        }
    }

    public static void capture(
            MessagesStorage storage,
            int account,
            LongSparseIntArray outbox,
            int observedAt
    ) {
        if (outbox == null || outbox.size() == 0 || !isEnabled(account) || observedAt <= 0) {
            return;
        }

        long owner = UserConfig.getInstance(account).getClientUserId();

        if (owner == 0) {
            return;
        }

        SQLiteDatabase archive = null;
        SQLiteStatement insert = null;
        int inserted = 0;

        try {
            archive = open(account);
            archive.beginTransaction();
            insert = archive.compileStatement(
                    "INSERT OR IGNORE INTO read_times(owner, dialog, message, read_at, exact) VALUES(?, ?, ?, ?, 0)"
            );

            for (int index = 0; index < outbox.size(); index++) {
                long dialogId = outbox.keyAt(index);
                int maxMessageId = outbox.valueAt(index);
                TLRPC.User user = dialogId > 0 ? storage.getUser(dialogId) : null;

                if (!ReadTimePolicy.shouldCaptureDialog(
                        owner,
                        dialogId,
                        user != null,
                        user != null && user.bot,
                        user != null && UserObject.isService(user.id),
                        user != null && UserObject.isReplyUser(user),
                        user != null && UserObject.isAnonymous(user))) {
                    continue;
                }

                SQLiteCursor cursor = null;

                try {
                    cursor = storage.getDatabase().queryFinalized(
                            "SELECT mid, date FROM messages_v2 WHERE uid = ? AND mid > 0 AND mid <= ? AND out = 1 AND read_state IN(0, 2) ORDER BY mid",
                            dialogId,
                            maxMessageId
                    );

                    while (cursor.next()) {
                        int readAt = ReadTimePolicy.readTime(cursor.intValue(1), observedAt);

                        if (readAt == 0) {
                            continue;
                        }

                        insert.clearBindings();
                        insert.bindLong(1, owner);
                        insert.bindLong(2, dialogId);
                        insert.bindLong(3, cursor.intValue(0));
                        insert.bindLong(4, readAt);

                        if (insert.executeInsert() != -1) {
                            inserted++;
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
            }

            archive.setTransactionSuccessful();
        } catch (Exception error) {
            FileLog.e(error);
        } finally {
            if (insert != null) {
                try {
                    insert.close();
                } catch (Exception error) {
                    FileLog.e(error);
                }
            }

            if (archive != null) {
                try {
                    if (archive.inTransaction()) {
                        archive.endTransaction();
                    }
                } catch (Exception error) {
                    FileLog.e(error);
                }

                if (inserted > 0 && shouldTrim(account, inserted)) {
                    try {
                        trim(archive, owner);
                    } catch (Exception error) {
                        FileLog.e(error);
                    }
                }

                try {
                    archive.close();
                } catch (Exception error) {
                    FileLog.e(error);
                }
            }
        }
    }

    public static void load(int account, long dialog, int message, Consumer<Entry> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();

        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            Entry result = null;

            try {
                if (owner != 0 && databaseFile(account).exists()) {
                    try (SQLiteDatabase database = open(account);
                            Cursor cursor = database.rawQuery(
                                    "SELECT read_at, exact FROM read_times WHERE owner = ? AND dialog = ? AND message = ? LIMIT 1",
                                    new String[] {
                                            Long.toString(owner),
                                            Long.toString(dialog),
                                            Integer.toString(message)
                                    })) {
                        if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                            result = new Entry(cursor.getInt(0), cursor.getInt(1) != 0);
                        }
                    }
                }
            } catch (Exception error) {
                FileLog.e(error);
            }

            Entry entry = result;

            AndroidUtilities.runOnUIThread(() -> {
                if (owner == UserConfig.getInstance(account).getClientUserId()) {
                    callback.accept(entry);
                }
            });
        });
    }

    public static synchronized void clearAccount(int account) {
        preferences(account).edit().clear().apply();
        pendingTrim.delete(account);
        trimInitialized.delete(account);
        SQLiteDatabase.deleteDatabase(databaseFile(account));
    }

    private static SQLiteDatabase open(int account) {
        SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(databaseFile(account), null);
        database.execSQL("CREATE TABLE IF NOT EXISTS read_times (owner INTEGER NOT NULL, dialog INTEGER NOT NULL, message INTEGER NOT NULL, read_at INTEGER NOT NULL, exact INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(owner, dialog, message))");
        database.execSQL("CREATE INDEX IF NOT EXISTS read_times_age ON read_times(owner, read_at)");
        database.setVersion(1);

        return database;
    }

    private static void trim(SQLiteDatabase database, long owner) {
        int count = 0;

        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM read_times WHERE owner = ?",
                new String[] {Long.toString(owner)})) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        }

        int excess = count - MAX_RECORDS;

        if (excess > 0) {
            database.delete(
                    "read_times",
                    "rowid IN (SELECT rowid FROM read_times WHERE owner = ? ORDER BY read_at, rowid LIMIT "
                            + excess + ")",
                    new String[] {Long.toString(owner)}
            );
        }
    }

    private static synchronized boolean shouldTrim(int account, int inserted) {
        if (!trimInitialized.get(account)) {
            trimInitialized.put(account, true);
            pendingTrim.delete(account);
            return true;
        }

        int pending = pendingTrim.get(account) + inserted;

        if (pending < TRIM_INTERVAL) {
            pendingTrim.put(account, pending);
            return false;
        }

        pendingTrim.delete(account);
        return true;
    }

    private static synchronized SharedPreferences activePreferences(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();

        if (owner == 0) {
            return null;
        }

        SharedPreferences preferences = preferences(account);

        if (preferences.getLong(OWNER, 0) != owner) {
            preferences.edit().clear().putLong(OWNER, owner).apply();
        }

        return preferences;
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_read_times_" + account,
                Context.MODE_PRIVATE
        );
    }

    private static File databaseFile(int account) {
        return new File(
                ApplicationLoader.applicationContext.getNoBackupFilesDir(),
                "fluxgram_read_times_" + account + ".db"
        );
    }
}
