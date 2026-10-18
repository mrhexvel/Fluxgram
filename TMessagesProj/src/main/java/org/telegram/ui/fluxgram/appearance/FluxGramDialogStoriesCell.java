package org.telegram.ui.fluxgram.appearance;

import android.content.Context;

import androidx.annotation.NonNull;

import org.telegram.messenger.fluxgram.AppearanceSettings;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Stories.DialogStoriesCell;

public class FluxGramDialogStoriesCell extends DialogStoriesCell {
    private final int account;
    private final boolean appearanceEnabled;

    public FluxGramDialogStoriesCell(
            @NonNull Context context,
            BaseFragment fragment,
            int account,
            int type,
            boolean appearanceEnabled
    ) {
        super(context, fragment, account, type);
        this.account = account;
        this.appearanceEnabled = appearanceEnabled;
        updateItems(false, false);
    }

    @Override
    protected CharSequence getCollapsedHeaderTitleOverride() {
        return appearanceEnabled ? AppearanceSettings.getActionBarTitle(account) : null;
    }

    @Override
    protected boolean isCollapsedHeaderTitleCentered() {
        return appearanceEnabled && AppearanceSettings.centersActionBarTitle(account);
    }

    @Override
    protected boolean isCollapsedHeaderStatusHidden() {
        return appearanceEnabled && AppearanceSettings.hidesActionBarStatus(account);
    }
}
