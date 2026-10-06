package org.telegram.messenger.fluxgram;

import java.util.HashSet;
import java.util.Set;

public final class DeletedMessagesScope {
    private static final long FOLDER_FLAG = 0x2000000000000000L;
    private static final long ENCRYPTED_FLAG = 0x4000000000000000L;
    private static final long SIGN_FLAG = 0x8000000000000000L;

    private final long owner;
    private final boolean allChats;
    private final HashSet<Long> chats;

    public DeletedMessagesScope(long owner, boolean allChats, Set<Long> chats) {
        this.owner = owner;
        this.allChats = allChats;
        this.chats = new HashSet<>();

        if (chats != null) {
            for (long dialog : chats) {
                if (validDialog(dialog)) {
                    this.chats.add(dialog);
                }
            }
        }
    }

    public boolean allows(long currentOwner, long dialog) {
        return owner != 0 && owner == currentOwner && validDialog(dialog)
                && (allChats || chats.contains(dialog));
    }

    public static boolean validDialog(long dialog) {
        return dialog != 0 && !isSpecial(dialog, FOLDER_FLAG) && !isSpecial(dialog, ENCRYPTED_FLAG);
    }

    private static boolean isSpecial(long dialog, long flag) {
        return (dialog & flag) != 0 && (dialog & SIGN_FLAG) == 0;
    }
}
