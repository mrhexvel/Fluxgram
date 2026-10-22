package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.fluxgram.GhostModeDialogScope;
import org.telegram.messenger.fluxgram.GhostModeSettings;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.InviteMembersBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;

import java.util.ArrayList;
import java.util.HashSet;

public final class FluxGramGhostModeActivity extends BaseFragment {
    private static final int ENABLED = 1;
    private static final int HIDE_ONLINE = 2;
    private static final int HIDE_TYPING = 3;
    private static final int HIDE_READ_RECEIPTS = 4;
    private static final int HIDE_STORY_VIEWS = 5;
    private static final int AUTOMATIC_OFFLINE = 6;
    private static final int READ_ON_ACTIONS = 7;
    private static final int USE_SEND_DELAY = 8;
    private static final int SILENT_SEND_MODE = 9;
    private static final int SUGGEST_FOR_STORIES = 10;
    private static final int WHITELIST = 11;
    private static final int BLACKLIST = 12;

    private UniversalRecyclerView listView;
    private boolean expanded = false;

    @Override
    public View createView(Context context) {
        actionBar.setTitle(text(R.string.FluxGramGhostMode));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = content;

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        FluxGramSettingsSections.apply(listView, currentAccount);
        content.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT
        ));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean enabled = GhostModeSettings.isEnabled(currentAccount);
        boolean hideOnline = GhostModeSettings.hidesOnline(currentAccount);
        boolean hideTyping = GhostModeSettings.hidesTyping(currentAccount);
        boolean hideReadReceipts = GhostModeSettings.hidesReadReceipts(currentAccount);
        boolean hideStoryViews = GhostModeSettings.hidesStoryViews(currentAccount);
        boolean automaticOffline = GhostModeSettings.usesAutomaticOffline(currentAccount);
        int selected = (hideOnline ? 1 : 0) + (hideTyping ? 1 : 0)
                + (hideReadReceipts ? 1 : 0) + (hideStoryViews ? 1 : 0)
                + (automaticOffline ? 1 : 0);

        HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        label.setText(text(R.string.FluxGramGhostMode));
        items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asExpandableSwitch(
                        ENABLED, text(R.string.FluxGramGhostMode), selected + "/5"
                )
                .setChecked(enabled)
                .setCollapsed(!expanded)
                .setClickCallback(view -> updateSettings(() -> GhostModeSettings.setEnabled(
                        currentAccount, !GhostModeSettings.isEnabled(currentAccount)
                ))));

        if (expanded) {
            items.add(UItem.asCheck(HIDE_READ_RECEIPTS, text(R.string.FluxGramGhostModeHideReadReceipts))
                    .setChecked(hideReadReceipts));
            items.add(UItem.asCheck(HIDE_STORY_VIEWS, text(R.string.FluxGramGhostModeHideStoryViews))
                    .setChecked(hideStoryViews));
            items.add(UItem.asCheck(HIDE_ONLINE, text(R.string.FluxGramGhostModeHideOnline))
                    .setChecked(hideOnline));
            items.add(UItem.asCheck(HIDE_TYPING, text(R.string.FluxGramGhostModeHideTyping))
                    .setChecked(hideTyping));
            items.add(UItem.asCheck(AUTOMATIC_OFFLINE, text(R.string.FluxGramGhostModeAutomaticOffline))
                    .setChecked(automaticOffline));
            items.add(UItem.asShadow(null));
        }

        items.add(UItem.asCheck(READ_ON_ACTIONS, text(R.string.FluxGramGhostModeReadOnActions))
                .setChecked(GhostModeSettings.readsOnActions(currentAccount)));
        items.add(UItem.asShadow(text(R.string.FluxGramGhostModeReadOnActionsInfo)));

        items.add(UItem.asCheck(USE_SEND_DELAY, text(R.string.FluxGramGhostModeUseSendDelay))
                .setChecked(GhostModeSettings.usesSendDelay(currentAccount)));
        items.add(UItem.asShadow(text(R.string.FluxGramGhostModeUseSendDelayInfo)));

        items.add(UItem.asButton(
                SILENT_SEND_MODE,
                text(R.string.FluxGramGhostModeSendSilently),
                silentSendModeText(GhostModeSettings.getSilentSendMode(currentAccount))
        ));
        items.add(UItem.asShadow(text(R.string.FluxGramGhostModeSendSilentlyInfo)));

        items.add(UItem.asCheck(SUGGEST_FOR_STORIES, text(R.string.FluxGramGhostModeSuggestForStories))
                .setChecked(GhostModeSettings.suggestsForStories(currentAccount)));
        items.add(UItem.asShadow(text(R.string.FluxGramGhostModeSuggestForStoriesInfo)));

        items.add(UItem.asButton(
                WHITELIST,
                text(R.string.FluxGramGhostModeWhitelist),
                chatCount(GhostModeDialogScope.whitelist(currentAccount).size())
        ));
        items.add(UItem.asButton(
                BLACKLIST,
                text(R.string.FluxGramGhostModeBlacklist),
                chatCount(GhostModeDialogScope.blacklist(currentAccount).size())
        ));
        items.add(UItem.asShadow(text(R.string.FluxGramGhostModeDialogListsInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ENABLED) {
            expanded = !expanded;
            listView.adapter.update(true);
            return;
        }

        if (item.id == SILENT_SEND_MODE) {
            showSilentSendModeDialog();
            return;
        }
        if (item.id == WHITELIST || item.id == BLACKLIST) {
            showDialogSelection(item.id == WHITELIST);
            return;
        }

        boolean checked = !item.checked;

        if (item.id == HIDE_ONLINE) {
            updateSettings(() -> GhostModeSettings.setHidesOnline(currentAccount, checked));
        } else if (item.id == HIDE_TYPING) {
            updateSettings(() -> GhostModeSettings.setHidesTyping(currentAccount, checked));
        } else if (item.id == HIDE_READ_RECEIPTS) {
            updateSettings(() -> GhostModeSettings.setHidesReadReceipts(currentAccount, checked));
        } else if (item.id == HIDE_STORY_VIEWS) {
            updateSettings(() -> GhostModeSettings.setHidesStoryViews(currentAccount, checked));
        } else if (item.id == AUTOMATIC_OFFLINE) {
            updateSettings(() -> GhostModeSettings.setUsesAutomaticOffline(currentAccount, checked));
        } else if (item.id == READ_ON_ACTIONS) {
            updateSettings(() -> GhostModeSettings.setReadsOnActions(currentAccount, checked));
        } else if (item.id == USE_SEND_DELAY) {
            updateSettings(() -> GhostModeSettings.setUsesSendDelay(currentAccount, checked));
        } else if (item.id == SUGGEST_FOR_STORIES) {
            updateSettings(() -> GhostModeSettings.setSuggestsForStories(currentAccount, checked));
        } else {
            return;
        }
    }

    private void updateSettings(Runnable mutation) {
        boolean hiddenBefore = GhostModeSettings.shouldHideOnline(currentAccount);
        boolean automaticOfflineBefore = GhostModeSettings.shouldUseAutomaticOffline(currentAccount);
        mutation.run();

        if (hiddenBefore != GhostModeSettings.shouldHideOnline(currentAccount)
                || automaticOfflineBefore != GhostModeSettings.shouldUseAutomaticOffline(currentAccount)) {
            MessagesController.getInstance(currentAccount).updateGhostModeOnlineStatus();
        }

        listView.adapter.update(true);
    }

    private void showSilentSendModeDialog() {
        CharSequence[] options = {
                text(R.string.FluxGramGhostModeSendSilentlyNever),
                text(R.string.FluxGramGhostModeSendSilentlyInGhostMode),
                text(R.string.FluxGramGhostModeSendSilentlyAlways)
        };
        int selected = GhostModeSettings.getSilentSendMode(currentAccount);

        showDialog(AlertsCreator.createSingleChoiceDialog(
                getParentActivity(),
                options,
                text(R.string.FluxGramGhostModeSendSilently),
                selected,
                (dialog, index) -> {
                    GhostModeSettings.setSilentSendMode(currentAccount, index);
                    listView.adapter.update(true);
                },
                getResourceProvider()
        ));
    }

    private void showDialogSelection(boolean whitelist) {
        int account = currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        ArrayList<Long> selected = availableDialogs(
                whitelist
                        ? GhostModeDialogScope.whitelist(account)
                        : GhostModeDialogScope.blacklist(account),
                account
        );
        LongSparseArray<TLObject> ignored = new LongSparseArray<>();
        TLRPC.User self = MessagesController.getInstance(account).getUser(owner);
        if (self != null) {
            ignored.put(owner, self);
        }
        InviteMembersBottomSheet picker = new InviteMembersBottomSheet(
                getParentActivity(), account, ignored, 0, this, getResourceProvider()
        );
        picker.setDelegate(dialogs -> {
            if (!activeAccount(account, owner)) {
                return;
            }

            if (whitelist) {
                GhostModeDialogScope.setWhitelist(account, new HashSet<>(dialogs));
            } else {
                GhostModeDialogScope.setBlacklist(account, new HashSet<>(dialogs));
            }
            listView.adapter.update(false);
        }, selected);
        picker.setSelectedContacts(selected);
        showDialog(picker);
    }

    private ArrayList<Long> availableDialogs(HashSet<Long> dialogs, int account) {
        ArrayList<Long> result = new ArrayList<>();
        MessagesController controller = MessagesController.getInstance(account);

        for (long dialogId : dialogs) {
            if ((DialogObject.isUserDialog(dialogId) && controller.getUser(dialogId) != null)
                    || (DialogObject.isChatDialog(dialogId) && controller.getChat(-dialogId) != null)) {
                result.add(dialogId);
            }
        }

        return result;
    }

    private boolean activeAccount(int account, long owner) {
        return !isFinished && listView != null && getParentActivity() != null
                && currentAccount == account && owner != 0
                && owner == UserConfig.getInstance(account).getClientUserId();
    }

    private String chatCount(int count) {
        return LocaleController.formatPluralString("Chats", count, count);
    }

    private String silentSendModeText(int mode) {
        if (mode == GhostModeSettings.SILENT_SEND_IN_GHOST_MODE) {
            return text(R.string.FluxGramGhostModeSendSilentlyInGhostMode);
        }
        if (mode == GhostModeSettings.SILENT_SEND_ALWAYS) {
            return text(R.string.FluxGramGhostModeSendSilentlyAlways);
        }

        return text(R.string.FluxGramGhostModeSendSilentlyNever);
    }

    private String text(int resource) {
        return LocaleController.getString(resource);
    }
}
