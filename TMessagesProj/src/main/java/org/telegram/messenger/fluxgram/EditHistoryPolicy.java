package org.telegram.messenger.fluxgram;

import java.util.Arrays;

public final class EditHistoryPolicy {
    public static boolean shouldRecord(int previousEditDate, int incomingEditDate,
            byte[] previousContent, byte[] incomingContent) {
        return incomingEditDate > 0
                && incomingEditDate >= previousEditDate
                && !Arrays.equals(previousContent, incomingContent);
    }
}
