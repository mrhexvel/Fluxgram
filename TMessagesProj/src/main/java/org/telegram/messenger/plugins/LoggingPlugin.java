package org.telegram.messenger.plugins;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/** Diagnostic plugin: logs metadata only, never the message text. */
public final class LoggingPlugin implements Plugin {
    public static final String ID = "fluxgram.logging";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getName() {
        return LocaleController.getString(R.string.FluxGramLogging);
    }

    @Override
    public String getDescription() {
        return LocaleController.getString(R.string.FluxGramLoggingInfo);
    }

    @Override
    public String getVersion() {
        return "1.0.0";
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
