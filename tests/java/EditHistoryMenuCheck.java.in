import java.util.ArrayList;
import java.util.function.Consumer;

public final class EditHistoryMenuCheck {
    public static void main(String[] args) {
        EditHistoryMenuController controller = new EditHistoryMenuController();
        MessageObject message = new MessageObject();
        ChatMessageCell cell = new ChatMessageCell();
        cell.message = message;
        int[] opened = {0};

        controller.prepare(0, cell, message, true, () -> true, () -> {
            expect(controller.hasVersions(message));
            expect(!controller.prepare(0, cell, message, true, () -> true, () -> {}));
            opened[0]++;
        });
        EditHistory.reply(true);
        expect(opened[0] == 1 && !controller.hasVersions(message));
        expect(EditHistory.callbacks.isEmpty());

        controller.prepare(0, cell, message, true, () -> true, () -> {
            expect(!controller.hasVersions(message));
            opened[0]++;
        });
        EditHistory.reply(false);
        expect(opened[0] == 2);

        Runnable unexpected = () -> { throw new AssertionError("Stale menu opened"); };
        controller.prepare(0, cell, message, true, () -> true, unexpected);
        controller.cancel();
        EditHistory.reply(true);

        controller.prepare(0, cell, message, true, () -> true, unexpected);
        controller.prepare(0, cell, message, true, () -> true, () -> opened[0]++);
        EditHistory.reply(true);
        EditHistory.reply(true);
        expect(opened[0] == 3);

        controller.prepare(0, cell, message, true, () -> true, unexpected);
        cell.message = new MessageObject();
        EditHistory.reply(true);
        cell.message = message;

        controller.prepare(0, cell, message, true, () -> true, unexpected);
        cell.attached = false;
        EditHistory.reply(true);
        cell.attached = true;

        boolean[] active = {true};
        controller.prepare(0, cell, message, true, () -> active[0], unexpected);
        active[0] = false;
        EditHistory.reply(true);

        controller.prepare(0, cell, message, true, () -> true, unexpected);
        expect(!controller.prepare(0, cell, message, false, () -> true, unexpected));
        EditHistory.reply(true);

        controller.prepare(0, cell, message, true, () -> true, () -> {
            throw new IllegalStateException("Menu failed");
        });
        try {
            EditHistory.reply(true);
            throw new AssertionError("Expected failure");
        } catch (IllegalStateException expected) {
            expect(!controller.hasVersions(message));
        }

        ChatActionCell action = new ChatActionCell();
        action.message = message;
        controller.prepare(0, action, message, true, () -> true, unexpected);
        action.message = new MessageObject();
        EditHistory.reply(true);
        expect(EditHistory.callbacks.isEmpty());
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
