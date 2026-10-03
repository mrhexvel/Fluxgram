package org.telegram.messenger.plugins;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

/** Diagnostic plugin: logs metadata only, never the message text. */
public final class LoggingPlugin implements Plugin {
    public static final String ID = "fluxgram.logging";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void beforeSend(BeforeSendEvent event) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("FluxGram before_send: account=" + event.account
                    + ", length=" + event.text.length()
                    + ", scheduled=" + (event.scheduleDate != 0));
        }
    }
}
