package org.telegram.messenger.fluxgram;

import org.telegram.messenger.AndroidUtilities;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class DeletedMessagesRetention {
    private final Map<Long, Set<Integer>> dialogs;

    private DeletedMessagesRetention(Map<Long, ? extends Set<Integer>> source) {
        Map<Long, Set<Integer>> snapshot = new HashMap<>();

        for (Map.Entry<Long, ? extends Set<Integer>> entry : source.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                snapshot.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
            }
        }

        dialogs = Collections.unmodifiableMap(snapshot);
    }

    public static DeletedMessagesRetention copyOf(Map<Long, ? extends Set<Integer>> source) {
        return new DeletedMessagesRetention(source);
    }

    public Selection forChat(long channelId, boolean channelChat, long dialogId, long mergeDialogId) {
        if (channelId != 0 && channelId != -dialogId) {
            return null;
        }

        long targetDialog = channelId == 0 && channelChat ? mergeDialogId : dialogId;
        Set<Integer> ids = dialogs.get(targetDialog);

        if (ids == null) {
            return null;
        }

        int dictionary = targetDialog == mergeDialogId && mergeDialogId != 0 ? 1 : 0;
        return new Selection(targetDialog, dictionary, ids);
    }

    public static final class Selection {
        public final long dialogId;
        public final int dictionary;
        private final Set<Integer> ids;

        private Selection(long dialogId, int dictionary, Set<Integer> ids) {
            this.dialogId = dialogId;
            this.dictionary = dictionary;
            this.ids = ids;
        }

        public boolean contains(int messageId) {
            return ids.contains(messageId);
        }
    }

    public static final class Builder {
        private final Map<Long, HashSet<Integer>> dialogs = new HashMap<>();

        public void add(Map<Long, ? extends Set<Integer>> batch) {
            for (Map.Entry<Long, ? extends Set<Integer>> entry : batch.entrySet()) {
                dialogs.computeIfAbsent(entry.getKey(), key -> new HashSet<>()).addAll(entry.getValue());
            }
        }

        public void deliverOnUiThread(Consumer<DeletedMessagesRetention> callback) {
            DeletedMessagesRetention snapshot = copyOf(dialogs);
            AndroidUtilities.runOnUIThread(() -> callback.accept(snapshot));
        }
    }
}
