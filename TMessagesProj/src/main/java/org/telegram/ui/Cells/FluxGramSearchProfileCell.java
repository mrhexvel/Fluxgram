package org.telegram.ui.Cells;

import android.content.Context;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.AvatarGeometry;
import org.telegram.tgnet.TLRPC;

public class FluxGramSearchProfileCell extends ProfileSearchCell {
    protected final int account;
    private final boolean avatarGeometry;
    private boolean avatarGeometryEnabled;

    public FluxGramSearchProfileCell(Context context, int account, boolean avatarGeometry) {
        super(context);
        this.account = account;
        this.avatarGeometry = avatarGeometry;
        setCurrentAccount(account);
    }

    @Override
    public void setData(
            Object object,
            TLRPC.EncryptedChat encryptedChat,
            CharSequence name,
            CharSequence status,
            boolean needCount,
            boolean saved
    ) {
        avatarGeometryEnabled = avatarGeometry && isAvatarGeometryPeer(object, encryptedChat, saved);
        super.setData(object, encryptedChat, name, status, needCount, saved);
    }

    @Override
    protected int resolveAvatarRoundRadius(
            int stockRadius,
            int avatarSize,
            TLRPC.User user,
            TLRPC.Chat chat
    ) {
        if (!avatarGeometryEnabled) {
            return stockRadius;
        }

        return AvatarGeometry.resolveRadius(account, avatarSize, avatarRole(chat), stockRadius);
    }

    @Override
    protected float resolveStoryRoundRectRadius(
            int avatarRadius,
            TLRPC.User user,
            TLRPC.Chat chat
    ) {
        if (!avatarGeometryEnabled || AvatarGeometry.usesStockGeometry(account)) {
            return -1;
        }

        return avatarRadius;
    }

    protected boolean isAvatarGeometryPeer(
            Object object,
            TLRPC.EncryptedChat encryptedChat,
            boolean saved
    ) {
        if (encryptedChat != null || saved) {
            return false;
        }
        if (object instanceof TLRPC.User) {
            TLRPC.User user = (TLRPC.User) object;
            return user.id > 0
                    && !UserObject.isDeleted(user)
                    && !UserObject.isUserSelf(user)
                    && !UserObject.isReplyUser(user)
                    && !UserObject.isAnonymous(user)
                    && !UserObject.isService(user.id)
                    && user.id != UserObject.VERIFY
                    && user.id != UserObject.OAUTH;
        }
        return object instanceof TLRPC.Chat && ((TLRPC.Chat) object).id > 0;
    }

    private static int avatarRole(TLRPC.Chat chat) {
        if (chat == null) {
            return AvatarGeometry.ROLE_USER;
        }
        if (ChatObject.isMonoForum(chat)) {
            return AvatarGeometry.ROLE_MONOFORUM;
        }
        if (ChatObject.isCommunity(chat)) {
            return AvatarGeometry.ROLE_COMMUNITY;
        }
        if (ChatObject.isForum(chat)) {
            return AvatarGeometry.ROLE_FORUM;
        }
        return AvatarGeometry.ROLE_GROUP;
    }
}
