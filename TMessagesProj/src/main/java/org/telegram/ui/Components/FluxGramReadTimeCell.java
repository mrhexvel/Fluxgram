package org.telegram.ui.Components;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.ReadTimeArchive;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramReadTimeCell extends ActionBarMenuSubItem {
    public FluxGramReadTimeCell(Context context, ReadTimeArchive.Entry entry,
            Theme.ResourcesProvider resourcesProvider) {
        super(context, false, false, resourcesProvider);

        CharSequence title = LocaleController.getString(R.string.FluxGramReadTimeConfirmation);
        CharSequence date = LocaleController.formatString(
                R.string.FluxGramReadTimeObserved,
                LocaleController.formatDateAudio(entry.readAt, true)
        );

        setItemHeight(64);
        setMinimumWidth(AndroidUtilities.dp(240));
        setTextAndIcon(title, R.drawable.msg_seen);
        setSubtext(date);
        setBackground(null);
        setClickable(false);
        setFocusable(false);
        setContentDescription(title + ", " + date);
    }
}
