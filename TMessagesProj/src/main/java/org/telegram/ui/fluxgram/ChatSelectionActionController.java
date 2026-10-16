package org.telegram.ui.fluxgram;

import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.plugins.PluginChatSelectionActions;
import org.telegram.messenger.plugins.api.ChatSelectionMessageSnapshot;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class ChatSelectionActionController {
    public static final int MENU_ITEM_ID = 0x4653474d;

    private final int account;
    private final long ownerId;
    private final BooleanSupplier active;
    private final PluginChatSelectionActions.Surface surface;
    private ActionBarMenuItem itemView;
    private PluginChatSelectionActions.ActionItem displayedAction;

    public ChatSelectionActionController(int account, long ownerId,
            BooleanSupplier active, Runnable invalidationListener) {
        this.account = account;
        this.ownerId = ownerId;
        this.active = active;
        surface = PluginChatSelectionActions.openSurface(invalidationListener);
    }

    public void attach(ActionBarMenu actionMode, List<View> actionModeViews, int itemWidth) {
        itemView = actionMode.addItemWithWidth(
                MENU_ITEM_ID,
                R.drawable.msg_customize,
                itemWidth,
                ""
        );
        itemView.setVisibility(View.GONE);
        actionModeViews.add(itemView);
    }

    public void update(MessageObject message) {
        displayedAction = null;

        if (itemView == null) {
            return;
        }

        if (!isActive() || message == null) {
            itemView.setVisibility(View.GONE);
            return;
        }

        ChatSelectionMessageSnapshot snapshot = snapshot(message);
        PluginChatSelectionActions.ActionItem action = surface.findAction(snapshot);

        if (action == null) {
            itemView.setVisibility(View.GONE);
            return;
        }

        displayedAction = action;
        itemView.setIcon(action.iconResource);
        itemView.setContentDescription(action.title);
        itemView.setVisibility(View.VISIBLE);
    }

    public boolean handleClick(int id, MessageObject message) {
        if (id != MENU_ITEM_ID || !isActive() || message == null) {
            return false;
        }

        PluginChatSelectionActions.ActionItem action = displayedAction;
        ChatSelectionMessageSnapshot snapshot = snapshot(message);

        if (action == null || !action.matches(snapshot)) {
            update(message);
            action = displayedAction;
        }

        return action != null && surface.perform(action, snapshot);
    }

    public void destroy() {
        itemView = null;
        displayedAction = null;

        try {
            surface.close();
        } catch (Exception | LinkageError | AssertionError error) {
            FileLog.e(error);
        }
    }

    private boolean isActive() {
        return ownerId > 0
                && UserConfig.getInstance(account).getClientUserId() == ownerId
                && active.getAsBoolean();
    }

    private static ChatSelectionMessageSnapshot snapshot(MessageObject message) {
        long rawSenderId = message.getFromChatId();
        return new ChatSelectionMessageSnapshot(
                message.getId(),
                message.getDialogId(),
                rawSenderId == 0 ? null : rawSenderId
        );
    }
}
