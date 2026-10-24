package org.telegram.messenger;

import android.content.SharedPreferences;

public final class MessagesController {
    public static SharedPreferences preferences;

    public static SharedPreferences getGlobalMainSettings() {
        return preferences;
    }
}
