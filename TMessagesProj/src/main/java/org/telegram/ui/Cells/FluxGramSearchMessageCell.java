package org.telegram.ui.Cells;

import android.content.Context;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.AvatarGeometry;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class FluxGramSearchMessageCell extends DialogCell {
    protected final int account;
    private final boolean avatarGeometry;

    public FluxGramSearchMessageCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            boolean avatarGeometry
    ) {
        super(null, context, false, true, account, resourcesProvider);
        this.account = account;
        this.avatarGeometry = avatarGeometry;
    }

    @Override
    public boolean isForumCell() {
        return false;
    }

    @Override
    protected int resolveAvatarRoundRadius(int stockRadius, int avatarSize, int avatarRole) {
        if (!avatarGeometry || !isAvatarGeometryDialog()) {
            return stockRadius;
        }

        int role = useFromUserAsAvatar ? AvatarGeometry.ROLE_USER : avatarRole;
        return AvatarGeometry.resolveRadius(account, avatarSize, role, stockRadius);
    }

    @Override
    protected float resolveStoryRoundRectRadius(int avatarRadius, int avatarRole) {
        if (!avatarGeometry
                || !isAvatarGeometryDialog()
                || AvatarGeometry.usesStockGeometry(account)) {
            return -1;
        }

        return avatarRadius;
    }

    private boolean isAvatarGeometryDialog() {
        long dialogId = getDialogId();
        if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(dialogId);
            return user != null
                    && user.id > 0
                    && !UserObject.isDeleted(user)
                    && !UserObject.isUserSelf(user)
                    && !UserObject.isReplyUser(user)
                    && !UserObject.isAnonymous(user)
                    && !UserObject.isService(user.id)
                    && user.id != UserObject.VERIFY
                    && user.id != UserObject.OAUTH;
        }
        return dialogId < 0 && MessagesController.getInstance(account).getChat(-dialogId) != null;
    }
}
