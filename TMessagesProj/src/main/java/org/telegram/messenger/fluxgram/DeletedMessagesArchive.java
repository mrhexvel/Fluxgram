package org.telegram.messenger.fluxgram;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DeletedMessagesArchive {
    private static final int BATCH_SIZE = 200;
    private final MessagesStorage storage;
    private final int account;
    private SQLiteDatabase initializedDatabase;
    private final HashMap<String, String> columnLists = new HashMap<>();

    public DeletedMessagesArchive(MessagesStorage storage, int account) {
        this.storage = storage;
        this.account = account;
    }

    public static String boundaryQuery(String table, boolean maximum, String where) {
        String aggregate = maximum ? "max" : "min";
        boolean topics = table.equals("fg_timeline_topics");

        if (!topics && !table.equals("fg_timeline_messages")) {
            return "SELECT " + aggregate + "(mid) FROM " + table + " WHERE " + where;
        }

        String original = topics ? "messages_topics" : "messages_v2";
        String archive = topics ? "fg_deleted_topics" : "fg_deleted_messages";
        return "SELECT " + aggregate + "(boundary) FROM (SELECT " + aggregate + "(mid) AS boundary FROM "
                + original + " WHERE " + where + " UNION ALL SELECT " + aggregate + "(mid) AS boundary FROM "
                + archive + " WHERE " + where + ")";
    }

    public boolean prepare() {
        SQLiteDatabase db = storage.getDatabase();

        if (db == initializedDatabase) {
            return db != null;
        }

        try {
            prepareTable(db, "messages_v2", "fg_deleted_messages", "fg_timeline_messages", false);
            prepareTable(db, "messages_topics", "fg_deleted_topics", "fg_timeline_topics", true);
            initializedDatabase = db;
            return true;
        } catch (Exception error) {
            FileLog.e(error);
            return false;
        }
    }

    long countOnStorageQueue() throws Exception {
        if (!prepare()) {
            throw new IllegalStateException("Deleted message archive is unavailable");
        }

        SQLiteCursor cursor = storage.getDatabase().queryFinalized("SELECT COUNT(*) FROM fg_deleted_messages");

        try {
            return cursor.next() ? cursor.longValue(0) : 0;
        } finally {
            cursor.dispose();
        }
    }

    private void prepareTable(SQLiteDatabase db, String original, String archive, String view, boolean topics) throws Exception {
        db.executeFast("CREATE TABLE IF NOT EXISTS " + archive + " AS SELECT * FROM " + original + " WHERE 0").stepThis().dispose();
        LinkedHashMap<String, String> columns = columns(db, original);
        Map<String, String> archivedColumns = columns(db, archive);

        for (Map.Entry<String, String> column : columns.entrySet()) {
            if (!archivedColumns.containsKey(column.getKey())) {
                db.executeFast("ALTER TABLE " + archive + " ADD COLUMN " + column.getKey() + " " + column.getValue()).stepThis().dispose();
            }
        }

        String topic = topics ? ", topic_id" : "";
        db.executeFast("CREATE UNIQUE INDEX IF NOT EXISTS " + archive + "_key ON " + archive + "(uid" + topic + ", mid)").stepThis().dispose();
        db.executeFast("CREATE INDEX IF NOT EXISTS " + archive + "_date ON " + archive + "(uid" + topic + ", date, mid)").stepThis().dispose();
        db.executeFast("CREATE INDEX IF NOT EXISTS " + archive + "_mid ON " + archive + "(mid)").stepThis().dispose();
        String names = TextUtils.join(",", columns.keySet());
        columnLists.put(original, names);
        String live = "m." + names.replace(",", ",m.");
        String saved = "d." + names.replace(",", ",d.");
        db.executeFast("DROP VIEW IF EXISTS temp." + view).stepThis().dispose();
        db.executeFast("CREATE TEMP VIEW " + view + " AS SELECT " + live + ", 0 AS fluxgram_deleted FROM " + original + " m UNION ALL SELECT "
                + saved + ", 1 AS fluxgram_deleted FROM " + archive + " d INDEXED BY " + archive + "_date WHERE NOT EXISTS (SELECT 1 FROM " + original
                + " m WHERE m.uid = d.uid AND m.mid = d.mid" + (topics ? " AND m.topic_id = d.topic_id" : "") + ")").stepThis().dispose();
    }

    private LinkedHashMap<String, String> columns(SQLiteDatabase db, String table) throws Exception {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        SQLiteCursor cursor = db.queryFinalized("PRAGMA table_info(" + table + ")");

        try {
            while (cursor.next()) {
                result.put(cursor.stringValue(1), cursor.stringValue(2));
            }
        } finally {
            cursor.dispose();
        }

        return result;
    }

    public HashMap<Long, HashSet<Integer>> capture(long dialog, ArrayList<Integer> ids) {
        HashMap<Long, HashSet<Integer>> saved = new HashMap<>();

        if (ids == null || ids.isEmpty() || !prepare()) {
            return saved;
        }

        SQLiteDatabase db = storage.getDatabase();
        HashMap<Long, Boolean> allowedDialogs = new HashMap<>();
        DeletedMessagesScope deletedMessagesScope = EditHistory.deletedMessagesScope(account);

        for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
            String list = TextUtils.join(",", ids.subList(offset, Math.min(offset + BATCH_SIZE, ids.size())));
            String scope = scope(dialog);
            SQLiteCursor cursor = null;
            HashMap<Long, ArrayList<Integer>> accepted = new HashMap<>();
            ArrayList<TLRPC.Message> attachments = new ArrayList<>();

            try {
                cursor = db.queryFinalized("SELECT uid, mid FROM fg_deleted_messages WHERE mid IN(" + list + ")" + scope);

                while (cursor.next()) {
                    saved.computeIfAbsent(cursor.longValue(0), key -> new HashSet<>()).add(cursor.intValue(1));
                }

                cursor.dispose();
                cursor = null;

                if (!EditHistory.keepsDeleted(account)) {
                    continue;
                }

                cursor = db.queryFinalized("SELECT uid, mid, data, ttl FROM messages_v2 WHERE mid IN(" + list + ")" + scope);

                while (cursor.next()) {
                    long did = cursor.longValue(0);
                    int mid = cursor.intValue(1);

                    if (mid <= 0 || cursor.intValue(3) != 0 || DialogObject.isEncryptedDialog(did)) {
                        continue;
                    }

                    if (!deletedMessagesScope.allows(
                            UserConfig.getInstance(account).getClientUserId(), did)) {
                        continue;
                    }

                    Boolean allowed = allowedDialogs.get(did);

                    if (allowed == null) {
                        TLRPC.User user = did > 0 ? storage.getUser(did) : null;
                        allowed = did < 0 || did == UserConfig.getInstance(account).getClientUserId()
                                || user != null && (!user.bot || EditHistory.includesBots(account));
                        allowedDialogs.put(did, allowed);
                    }

                    if (!allowed) {
                        continue;
                    }

                    NativeByteBuffer data = cursor.byteBufferValue(2);

                    if (data == null) {
                        continue;
                    }

                    try {
                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(true), true);

                        if (!(message instanceof TLRPC.TL_message) || message.ttl != 0 || message.ttl_period != 0
                                || message.media != null && message.media.ttl_seconds != 0) {
                            continue;
                        }

                        if (SavedAttachments.get(account).isEnabled()) {
                            message.readAttachPath(data, UserConfig.getInstance(account).getClientUserId());
                            message.dialog_id = did;
                            message.id = mid;
                            attachments.add(message);
                        }
                    } finally {
                        data.reuse();
                    }

                    accepted.computeIfAbsent(did, key -> new ArrayList<>()).add(mid);
                }
            } catch (Exception error) {
                FileLog.e(error);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }

            if (accepted.isEmpty()) {
                continue;
            }

            boolean transaction = false;

            try {
                db.executeFast("SAVEPOINT fg_archive_batch").stepThis().dispose();
                transaction = true;

                for (Map.Entry<Long, ArrayList<Integer>> entry : accepted.entrySet()) {
                    String where = " WHERE uid = " + entry.getKey() + " AND mid IN(" + TextUtils.join(",", entry.getValue()) + ")";
                    copy(db, "messages_v2", "fg_deleted_messages", where);
                    copy(db, "messages_topics", "fg_deleted_topics", where);
                }

                db.executeFast("RELEASE fg_archive_batch").stepThis().dispose();
                transaction = false;

                for (TLRPC.Message message : attachments) {
                    SavedAttachments.get(account).capture(message);
                }

                for (Map.Entry<Long, ArrayList<Integer>> entry : accepted.entrySet()) {
                    saved.computeIfAbsent(entry.getKey(), key -> new HashSet<>()).addAll(entry.getValue());
                }
            } catch (Exception error) {
                FileLog.e(error);
            } finally {
                if (transaction) {
                    try {
                        db.executeFast("ROLLBACK TO fg_archive_batch").stepThis().dispose();
                        db.executeFast("RELEASE fg_archive_batch").stepThis().dispose();
                    } catch (Exception error) {
                        FileLog.e(error);
                    }
                }
            }

        }

        return saved;
    }

    private void copy(SQLiteDatabase db, String original, String archive, String where) throws Exception {
        String names = columnLists.get(original);
        db.executeFast("INSERT OR IGNORE INTO " + archive + "(" + names + ") SELECT " + names + " FROM " + original + where).stepThis().dispose();
        db.executeFast("UPDATE " + archive + " SET read_state = 3, mention = 0, send_state = 0" + where).stepThis().dispose();
    }

    private String scope(long dialog) {
        return dialog == 0 ? " AND is_channel = 0" : " AND uid = " + dialog;
    }

    public boolean remove(long dialog, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }

        if (!prepare()) {
            return false;
        }

        for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
            String list = TextUtils.join(",", ids.subList(offset, Math.min(offset + BATCH_SIZE, ids.size())));

            try {
                HashMap<Long, ArrayList<Integer>> removed = new HashMap<>();

                if (dialog == 0) {
                    SQLiteCursor cursor = storage.getDatabase().queryFinalized(
                            "SELECT uid, mid FROM fg_deleted_messages WHERE mid IN(" + list + ")" + scope(dialog));

                    try {
                        while (cursor.next()) {
                            removed.computeIfAbsent(cursor.longValue(0), key -> new ArrayList<>()).add(cursor.intValue(1));
                        }
                    } finally {
                        cursor.dispose();
                    }

                }

                for (String table : new String[] {"fg_deleted_messages", "fg_deleted_topics"}) {
                    storage.getDatabase().executeFast("DELETE FROM " + table + " WHERE mid IN(" + list + ")" + scope(dialog)).stepThis().dispose();
                }

                for (Map.Entry<Long, ArrayList<Integer>> entry : removed.entrySet()) {
                    SavedAttachments.get(account).remove(entry.getKey(), entry.getValue());
                }

                if (dialog != 0) {
                    EditHistory.removeMessages(account, dialog, ids.subList(offset, Math.min(offset + BATCH_SIZE, ids.size())));
                }
            } catch (Exception error) {
                FileLog.e(error);
                return false;
            }
        }

        SavedAttachments.get(account).remove(dialog, ids);
        return true;
    }

    public void removeDialog(long dialog) throws Exception {
        if (!prepare()) {
            throw new IllegalStateException("Deleted message archive is unavailable");
        }

        SQLiteDatabase db = storage.getDatabase();
        db.executeFast("DELETE FROM fg_deleted_messages WHERE uid = " + dialog).stepThis().dispose();
        db.executeFast("DELETE FROM fg_deleted_topics WHERE uid = " + dialog).stepThis().dispose();
        SavedAttachments.get(account).remove(dialog, null);
        EditHistory.removeDialog(account, dialog);
    }
}
