package org.telegram.messenger.fluxgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class GhostModeSendDelay {
    public static final long DELAY_MILLIS = 12000;

    private static final String DEADLINE_PARAM = "fluxgram_send_delay_deadline";

    private final int account;
    private final HashMap<Integer, PendingSend> pendingByMessageId = new HashMap<>();
    private final HashSet<PendingSend> pendingSends = new HashSet<>();
    private int generation;

    public GhostModeSendDelay(int account) {
        this.account = account;
    }

    public static HashMap<String, String> withDeadline(int account, HashMap<String, String> params, boolean eligible) {
        if (!eligible || !GhostModeSettings.shouldUseSendDelay(account)) {
            return params;
        }

        HashMap<String, String> result = params == null ? new HashMap<>() : new HashMap<>(params);
        result.put(DEADLINE_PARAM, Long.toString(System.currentTimeMillis() + DELAY_MILLIS));

        return result;
    }

    public boolean beforeSendingFinalRequest(MessageObject message, boolean scheduled, Runnable send) {
        ArrayList<MessageObject> messages = new ArrayList<>(1);
        messages.add(message);

        return beforeSendingFinalRequest(messages, scheduled, send);
    }

    public synchronized boolean beforeSendingFinalRequest(List<MessageObject> messages, boolean scheduled, Runnable send) {
        if (scheduled || messages == null || messages.isEmpty()) {
            return true;
        }

        long deadline = getDeadline(messages);

        if (deadline <= 0) {
            return true;
        }

        long remaining = deadline - System.currentTimeMillis();

        if (remaining <= 0) {
            clearDeadlines(messages);
            return true;
        }

        for (MessageObject message : messages) {
            if (pendingByMessageId.containsKey(message.getId())) {
                return false;
            }
        }

        long ownerId = UserConfig.getInstance(account).getClientUserId();

        if (ownerId == 0) {
            return true;
        }

        PendingSend pending = new PendingSend();
        pending.ownerId = ownerId;
        pending.generation = generation;
        pending.messages = new ArrayList<>(messages);
        pending.send = send;
        pending.release = () -> release(pending);

        pendingSends.add(pending);

        for (MessageObject message : pending.messages) {
            pendingByMessageId.put(message.getId(), pending);
        }

        AndroidUtilities.runOnUIThread(pending.release, Math.min(remaining, DELAY_MILLIS));

        return false;
    }

    public synchronized ArrayList<MessageObject> cancel(List<MessageObject> messages) {
        ArrayList<MessageObject> result = new ArrayList<>();
        HashSet<Integer> resultIds = new HashSet<>();

        for (MessageObject message : messages) {
            PendingSend pending = pendingByMessageId.get(message.getId());

            if (pending != null) {
                cancel(pending);

                for (MessageObject pendingMessage : pending.messages) {
                    if (resultIds.add(pendingMessage.getId())) {
                        result.add(pendingMessage);
                    }
                }
            } else if (resultIds.add(message.getId())) {
                result.add(message);
            }
        }

        return result;
    }

    public synchronized void cleanup() {
        generation++;

        for (PendingSend pending : new ArrayList<>(pendingSends)) {
            cancel(pending);
        }
    }

    private void release(PendingSend pending) {
        synchronized (this) {
            if (!pendingSends.contains(pending)
                    || pending.generation != generation
                    || UserConfig.getInstance(account).getClientUserId() != pending.ownerId) {
                return;
            }

            unregister(pending);
            clearDeadlines(pending.messages);
            pending.send.run();
        }
    }

    private void cancel(PendingSend pending) {
        AndroidUtilities.cancelRunOnUIThread(pending.release);
        unregister(pending);
    }

    private void unregister(PendingSend pending) {
        pendingSends.remove(pending);

        for (MessageObject message : pending.messages) {
            if (pendingByMessageId.get(message.getId()) == pending) {
                pendingByMessageId.remove(message.getId());
            }
        }
    }

    private static long getDeadline(List<MessageObject> messages) {
        long deadline = 0;

        for (MessageObject message : messages) {
            if (message == null || message.messageOwner == null || message.messageOwner.params == null) {
                continue;
            }

            String value = message.messageOwner.params.get(DEADLINE_PARAM);

            if (value == null) {
                continue;
            }

            try {
                deadline = Math.max(deadline, Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                message.messageOwner.params.remove(DEADLINE_PARAM);
            }
        }

        return deadline;
    }

    private static void clearDeadlines(List<MessageObject> messages) {
        for (MessageObject message : messages) {
            if (message != null && message.messageOwner != null && message.messageOwner.params != null) {
                message.messageOwner.params.remove(DEADLINE_PARAM);
            }
        }
    }

    private static final class PendingSend {
        private long ownerId;
        private int generation;
        private ArrayList<MessageObject> messages;
        private Runnable send;
        private Runnable release;
    }
}
