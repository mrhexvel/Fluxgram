import java.util.ArrayList;
import java.util.function.Consumer;

public final class MessageMenuMetadataCheck {
    public static void main(String[] args) {
        MessageMenuMetadataController controller = new MessageMenuMetadataController();
        MessageObject message = new MessageObject();
        ChatMessageCell cell = new ChatMessageCell();
        cell.message = message;
        int[] opened = {0};

        controller.prepare(0, cell, message, true, true, () -> true, () -> {
            expect(controller.hasVersions(message));
            expect(controller.getReadTime(message).readAt == 100);
            expect(!controller.prepare(0, cell, message, true, true, () -> true, () -> {}));
            opened[0]++;
        });
        EditHistory.reply(true);
        expect(opened[0] == 0);
        ReadTimeArchive.reply(new ReadTimeArchive.Entry(100, false));
        expect(opened[0] == 1 && !controller.hasVersions(message));
        expect(controller.getReadTime(message) == null);
        expect(EditHistory.callbacks.isEmpty());
        expect(ReadTimeArchive.callbacks.isEmpty());

        message.messageOwner.edit_date = 0;
        controller.prepare(0, cell, message, true, true, () -> true, () -> {
            expect(!controller.hasVersions(message));
            expect(controller.getReadTime(message) == null);
            opened[0]++;
        });
        expect(EditHistory.callbacks.isEmpty());
        ReadTimeArchive.reply(null);
        expect(opened[0] == 2);

        Runnable unexpected = () -> { throw new AssertionError("Stale menu opened"); };
        message.messageOwner.edit_date = 10;
        controller.prepare(0, cell, message, true, true, () -> true, unexpected);
        controller.cancel();
        EditHistory.reply(true);
        ReadTimeArchive.reply(new ReadTimeArchive.Entry(200, false));

        controller.prepare(0, cell, message, true, false, () -> true, unexpected);
        controller.prepare(0, cell, message, true, false, () -> true, () -> opened[0]++);
        EditHistory.reply(true);
        EditHistory.reply(true);
        expect(opened[0] == 3);

        controller.prepare(0, cell, message, true, false, () -> true, unexpected);
        cell.message = new MessageObject();
        EditHistory.reply(true);
        cell.message = message;

        controller.prepare(0, cell, message, true, false, () -> true, unexpected);
        cell.attached = false;
        EditHistory.reply(true);
        cell.attached = true;

        boolean[] active = {true};
        controller.prepare(0, cell, message, true, false, () -> active[0], unexpected);
        active[0] = false;
        EditHistory.reply(true);

        controller.prepare(0, cell, message, true, false, () -> true, unexpected);
        expect(!controller.prepare(0, cell, message, false, false, () -> true, unexpected));
        EditHistory.reply(true);

        controller.prepare(0, cell, message, true, true, () -> true, () -> {
            throw new IllegalStateException("Menu failed");
        });
        EditHistory.reply(true);
        try {
            ReadTimeArchive.reply(new ReadTimeArchive.Entry(300, false));
            throw new AssertionError("Expected failure");
        } catch (IllegalStateException expected) {
            expect(!controller.hasVersions(message));
            expect(controller.getReadTime(message) == null);
        }

        ChatActionCell action = new ChatActionCell();
        action.message = message;
        controller.prepare(0, action, message, true, false, () -> true, unexpected);
        action.message = new MessageObject();
        EditHistory.reply(true);
        expect(EditHistory.callbacks.isEmpty());
        expect(ReadTimeArchive.callbacks.isEmpty());
    }

    private static void expect(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }
}

class View {
    boolean attached = true;

    boolean isAttachedToWindow() {
        return attached;
    }
}

class MessageObject {
    final Owner messageOwner = new Owner();

    boolean isEphemeral() {
        return false;
    }

    int getId() {
        return 7;
    }

    long getDialogId() {
        return 42;
    }

    static class Owner {
        int edit_date = 10;
    }
}

class ChatMessageCell extends View {
    MessageObject message;

    MessageObject getMessageObject() {
        return message;
    }

    MessageObject getPrimaryMessageObject() {
        return message;
    }
}

class ChatActionCell extends View {
    MessageObject message;

    MessageObject getMessageObject() {
        return message;
    }
}

class EditHistory {
    static final ArrayList<Consumer<Boolean>> callbacks = new ArrayList<>();

    static void hasVersions(int account, long dialog, int message, Consumer<Boolean> callback) {
        callbacks.add(callback);
    }

    static void reply(boolean exists) {
        callbacks.remove(0).accept(exists);
    }
}

class ReadTimeArchive {
    static final ArrayList<Consumer<Entry>> callbacks = new ArrayList<>();

    static void load(int account, long dialog, int message, Consumer<Entry> callback) {
        callbacks.add(callback);
    }

    static void reply(Entry entry) {
        callbacks.remove(0).accept(entry);
    }

    static final class Entry {
        final int readAt;
        final boolean exact;

        Entry(int readAt, boolean exact) {
            this.readAt = readAt;
            this.exact = exact;
        }
    }
}
