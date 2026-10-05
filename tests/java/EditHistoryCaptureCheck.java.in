import java.util.ArrayList;

public final class EditHistoryCaptureCheck {
    public static void main(String[] args) {
        MessagesStorage storage = new MessagesStorage();
        ArrayList<TLRPC.Message> messages = new ArrayList<>();
        TLRPC.Message incoming = new TLRPC.TL_message();
        incoming.id = 7;
        incoming.edit_date = 10;
        incoming.dialog_id = 42;
        messages.add(incoming);

        EditHistoryCapture.capture(storage, 2, messages, false);
        EditHistory.enabled = false;
        EditHistoryCapture.capture(storage, 2, messages, true);
        EditHistory.enabled = true;
        EditHistoryCapture.capture(storage, 2, null, true);
        expect(storage.database.queries == 0 && EditHistory.saves == 0);

        incoming.edit_date = 0;
        EditHistoryCapture.capture(storage, 2, messages, true);
        incoming.edit_date = 10;
        incoming.id = -1;
        EditHistoryCapture.capture(storage, 2, messages, true);
        incoming.id = 7;
        incoming.dialog_id = Long.MIN_VALUE;
        EditHistoryCapture.capture(storage, 2, messages, true);
        expect(storage.database.queries == 0);

        incoming.dialog_id = 42;
        storage.user.bot = true;
        EditHistoryCapture.capture(storage, 2, messages, true);
        storage.user = null;
        EditHistoryCapture.capture(storage, 2, messages, true);
        expect(storage.database.queries == 0);

        EditHistory.bots = true;
        EditHistoryCapture.capture(storage, 2, messages, true);
        expect(EditHistory.saves == 1 && EditHistory.account == 2 && EditHistory.dialog == 42);
        expect(EditHistory.previous == storage.database.cursor.data.message);
        expect(EditHistory.previous.send_state == 3 && EditHistory.previous.attachOwner == 123);
        expect(storage.database.cursor.disposed && storage.database.cursor.data.reused);
        expect(storage.database.dialog == 42 && storage.database.mid == 7);

        EditHistory.bots = false;
        incoming.dialog_id = -42;
        storage.database.cursor = new SQLiteCursor();
        EditHistoryCapture.capture(storage, 2, messages, true);
        expect(EditHistory.saves == 2 && EditHistory.dialog == -42);

        storage.database.cursor = new SQLiteCursor();
        storage.database.cursor.data.broken = true;
        EditHistoryCapture.capture(storage, 2, messages, true);
        expect(EditHistory.previous == null);
        expect(storage.database.cursor.disposed && storage.database.cursor.data.reused);
    }

    private static void expect(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}

class MessagesStorage {
    final Database database = new Database();
    TLRPC.User user = new TLRPC.User();

    Database getDatabase() {
        return database;
    }
    TLRPC.User getUser(long id) {
        return user;
    }
}

class Database {
    int queries;
    long dialog;
    int mid;
    SQLiteCursor cursor = new SQLiteCursor();

    SQLiteCursor queryFinalized(String query, Object... args) {
        if (!query.equals("SELECT data, send_state FROM messages_v2 WHERE uid = ? AND mid = ?")) {
            throw new AssertionError(query);
        }
        queries++;
        dialog = (Long) args[0];
        mid = (Integer) args[1];
        return cursor;
    }
}

class SQLiteCursor {
    NativeByteBuffer data = new NativeByteBuffer();
    boolean disposed;

    boolean next() {
        return true;
    }
    NativeByteBuffer byteBufferValue(int column) {
        return data;
    }
    int intValue(int column) {
        return 3;
    }
    void dispose() {
        disposed = true;
    }
}

class NativeByteBuffer {
    TLRPC.Message message = new TLRPC.TL_message();
    boolean reused;
    boolean broken;

    int readInt32(boolean strict) {
        return 0;
    }
    void reuse() {
        reused = true;
    }
}

class TLRPC {
    static class Message {
        int id, edit_date, send_state;
        long dialog_id, attachOwner;

        static Message TLdeserialize(NativeByteBuffer data, int constructor, boolean strict) {
            if (data.broken) {
                throw new IllegalStateException();
            }
            return data.message;
        }

        void readAttachPath(NativeByteBuffer data, long owner) {
            attachOwner = owner;
        }
    }

    static class TL_message extends Message {}
    static class User {
        boolean bot;
    }
}

class DialogObject {
    static boolean isEncryptedDialog(long dialog) {
        return dialog == Long.MIN_VALUE;
    }
}

class MessageObject {
    static long getDialogId(TLRPC.Message message) {
        return message.dialog_id;
    }
}

class UserConfig {
    long clientUserId = 123;
    static UserConfig getInstance(int account) {
        return new UserConfig();
    }
}

class FileLog {
    static void e(Exception error) {}
}

class EditHistory {
    static boolean enabled = true, bots;
    static int saves, account;
    static long dialog;
    static TLRPC.Message previous;

    static boolean isEnabled(int account) {
        return enabled;
    }
    static boolean includesBots(int account) {
        return bots;
    }

    static void save(int a, long d, TLRPC.Message p, TLRPC.Message incoming) {
        saves++;
        account = a;
        dialog = d;
        previous = p;
    }
}
