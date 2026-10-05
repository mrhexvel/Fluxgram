package org.telegram.messenger.fluxgram;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import android.util.Base64;

public final class EditHistory {
    public interface Callback {
        void onLoaded(ArrayList<TLRPC.Message> versions, boolean failed);
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_history_" + account, Context.MODE_PRIVATE
        );
    }

    public static boolean isEnabled(int account) {
        return preferences(account).getBoolean("edits", false);
    }

    public static boolean includesBots(int account) {
        return preferences(account).getBoolean("bots", false);
    }

    public static void setEnabled(int account, boolean enabled) {
        preferences(account).edit().putBoolean("edits", enabled).apply();
    }

    public static void setIncludesBots(int account, boolean enabled) {
        preferences(account).edit().putBoolean("bots", enabled).apply();
    }

    private static File databaseFile(int account) {
        return new File(ApplicationLoader.applicationContext.getFilesDir(),
                "fluxgram_history_" + account + ".db");
    }

    private static SQLiteDatabase open(int account) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(databaseFile(account), null);
        db.execSQL("CREATE TABLE IF NOT EXISTS edit_versions (owner INTEGER NOT NULL, dialog INTEGER NOT NULL, message INTEGER NOT NULL, revision INTEGER NOT NULL, fingerprint TEXT NOT NULL, data BLOB NOT NULL, PRIMARY KEY(owner, dialog, message, revision, fingerprint))");
        db.setVersion(1);
        return db;
    }

    private static byte[] content(TLRPC.Message message) {
        SerializedData data = new SerializedData();

        try {
            data.writeString(message.message == null ? "" : message.message);
            data.writeInt32(message.entities.size());

            for (TLRPC.MessageEntity entity : message.entities) {
                entity.serializeToStream(data);
            }

            return data.toByteArray();
        } finally {
            data.cleanup();
        }
    }

    private static void restoreBeforePendingEdit(TLRPC.Message message) {
        if (message.params == null || !message.params.containsKey("prevMedia")) {
            return;
        }

        SerializedData data = new SerializedData(Base64.decode(message.params.get("prevMedia"), Base64.DEFAULT));

        try {
            message.media = TLRPC.MessageMedia.TLdeserialize(data, data.readInt32(true), true);
            message.message = data.readString(true);
            message.attachPath = data.readString(true);
            int count = data.readInt32(true);
            message.entities = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                message.entities.add(TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(true), true));
            }
        } finally {
            data.cleanup();
        }
    }

    public static void save(int account, long dialog, TLRPC.Message previous, TLRPC.Message incoming) {
        if (previous == null || !isEnabled(account)
                || incoming.params != null && incoming.params.containsKey("prevMedia")
                || incoming.edit_date <= 0 || incoming.edit_date < previous.edit_date
                || previous.id <= 0 || previous.ttl > 0 || incoming.ttl > 0
                || previous.media != null && previous.media.ttl_seconds > 0
                || incoming.media != null && incoming.media.ttl_seconds > 0) {
            return;
        }

        try {
            restoreBeforePendingEdit(previous);
            byte[] oldContent = content(previous);

            if (!EditHistoryPolicy.shouldRecord(previous.edit_date, incoming.edit_date,
                    oldContent, content(incoming))) {
                return;
            }

            StringBuilder fingerprint = new StringBuilder();

            for (byte value : MessageDigest.getInstance("SHA-256").digest(oldContent)) {
                fingerprint.append(Character.forDigit((value >> 4) & 15, 16));
                fingerprint.append(Character.forDigit(value & 15, 16));
            }

            SerializedData data = new SerializedData();
            byte[] snapshot;

            try {
                previous.serializeToStream(data);
                snapshot = data.toByteArray();
            } finally {
                data.cleanup();
            }

            ContentValues values = new ContentValues();
            values.put("owner", UserConfig.getInstance(account).getClientUserId());
            values.put("dialog", dialog);
            values.put("message", previous.id);
            values.put("revision", previous.edit_date == 0 ? previous.date : previous.edit_date);
            values.put("fingerprint", fingerprint.toString());
            values.put("data", snapshot);

            try (SQLiteDatabase db = open(account)) {
                db.insertWithOnConflict("edit_versions", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
        } catch (Exception error) {
            FileLog.e(error);
        }
    }

    public static void load(int account, long dialog, int message, Callback callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            ArrayList<TLRPC.Message> result = new ArrayList<>();
            boolean failed = false;

            try {
                if (databaseFile(account).exists()) {
                    try (SQLiteDatabase db = open(account);
                            Cursor cursor = db.rawQuery(
                                    "SELECT data FROM edit_versions WHERE owner = ? AND dialog = ? AND message = ? ORDER BY revision DESC, rowid DESC LIMIT 100",
                                    new String[] {Long.toString(owner), Long.toString(dialog), Integer.toString(message)})) {
                        while (cursor.moveToNext()) {
                            SerializedData data = new SerializedData(cursor.getBlob(0));

                            try {
                                TLRPC.Message version = TLRPC.Message.TLdeserialize(data, data.readInt32(true), true);
                                version.dialog_id = dialog;
                                result.add(version);
                            } finally {
                                data.cleanup();
                            }
                        }
                    }
                }
            } catch (Exception error) {
                FileLog.e(error);
                failed = true;
            }

            boolean loadFailed = failed;
            AndroidUtilities.runOnUIThread(() -> {
                if (owner == UserConfig.getInstance(account).getClientUserId()) {
                    callback.onLoaded(result, loadFailed);
                }
            });
        });
    }

    public static void clearAccount(int account) {
        preferences(account).edit().clear().apply();
        SQLiteDatabase.deleteDatabase(databaseFile(account));
    }
}
