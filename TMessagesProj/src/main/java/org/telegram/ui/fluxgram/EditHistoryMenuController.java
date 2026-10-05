package org.telegram.ui.fluxgram;

import android.view.View;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.fluxgram.EditHistory;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.function.BooleanSupplier;

public final class EditHistoryMenuController {
    private MessageObject checkedMessage;
    private boolean checkedHasVersions;
    private int requestId;

    public boolean prepare(int account, View cell, MessageObject message, boolean regularHistory,
            BooleanSupplier screenActive, Runnable openMenu) {
        if (checkedMessage == message) {
            return false;
        }

        int currentRequest = ++requestId;

        if (!regularHistory || message.isEphemeral()
                || message.getId() <= 0 || message.messageOwner.edit_date <= 0) {
            return false;
        }

        EditHistory.hasVersions(account, message.getDialogId(), message.getId(), hasVersions -> {
            if (currentRequest != requestId || !screenActive.getAsBoolean()
                    || !cell.isAttachedToWindow() || !showsMessage(cell, message)) {
                return;
            }

            checkedMessage = message;
            checkedHasVersions = hasVersions;

            try {
                openMenu.run();
            } finally {
                checkedMessage = null;
                checkedHasVersions = false;
            }
        });

        return true;
    }

    public boolean hasVersions(MessageObject message) {
        return checkedMessage == message && checkedHasVersions;
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
