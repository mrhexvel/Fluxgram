package org.telegram.ui.fluxgram;

import android.view.View;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.fluxgram.EditHistory;
import org.telegram.messenger.fluxgram.ReadTimeArchive;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.function.BooleanSupplier;

public final class MessageMenuMetadataController {
    private MessageObject checkedMessage;
    private boolean checkedHasVersions;
    private ReadTimeArchive.Entry checkedReadTime;
    private int requestId;

    public boolean prepare(int account, View cell, MessageObject message, boolean regularHistory,
            boolean localReadTime,
            BooleanSupplier screenActive, Runnable openMenu) {
        if (checkedMessage == message) {
            return false;
        }

        int currentRequest = ++requestId;
        boolean loadHistory = regularHistory && !message.isEphemeral()
                && message.getId() > 0 && message.messageOwner.edit_date > 0;
        boolean loadReadTime = localReadTime && !message.isEphemeral() && message.getId() > 0;

        if (!loadHistory && !loadReadTime) {
            return false;
        }

        int[] remaining = {0};
        boolean[] hasVersionsResult = {false};
        ReadTimeArchive.Entry[] readTimeResult = {null};

        if (loadHistory) {
            remaining[0]++;
        }
        if (loadReadTime) {
            remaining[0]++;
        }

        Runnable complete = () -> {
            remaining[0]--;

            if (remaining[0] != 0) {
                return;
            }

            if (currentRequest != requestId || !screenActive.getAsBoolean()
                    || !cell.isAttachedToWindow() || !showsMessage(cell, message)) {
                return;
            }

            checkedMessage = message;
            checkedHasVersions = hasVersionsResult[0];
            checkedReadTime = readTimeResult[0];

            try {
                openMenu.run();
            } finally {
                checkedMessage = null;
                checkedHasVersions = false;
                checkedReadTime = null;
            }
        };

        if (loadHistory) {
            EditHistory.hasVersions(account, message.getDialogId(), message.getId(), hasVersions -> {
                hasVersionsResult[0] = hasVersions;
                complete.run();
            });
        }

        if (loadReadTime) {
            ReadTimeArchive.load(account, message.getDialogId(), message.getId(), entry -> {
                readTimeResult[0] = entry;
                complete.run();
            });
        }

        return true;
    }

    public boolean hasVersions(MessageObject message) {
        return checkedMessage == message && checkedHasVersions;
    }

    public ReadTimeArchive.Entry getReadTime(MessageObject message) {
        return checkedMessage == message ? checkedReadTime : null;
    }

    public void cancel() {
        requestId++;
    }

    private boolean showsMessage(View cell, MessageObject message) {
        if (cell instanceof ChatMessageCell) {
            ChatMessageCell messageCell = (ChatMessageCell) cell;
            return messageCell.getMessageObject() == message
                    || messageCell.getPrimaryMessageObject() == message;
        }

        if (cell instanceof ChatActionCell) {
            return ((ChatActionCell) cell).getMessageObject() == message;
        }

        return true;
    }
}
