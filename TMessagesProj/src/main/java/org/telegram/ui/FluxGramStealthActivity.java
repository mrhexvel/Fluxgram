package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fluxgram.EditHistory;
import org.telegram.messenger.fluxgram.LocalStorageCleanup;
import org.telegram.messenger.fluxgram.LocalStorageStatistics;
import org.telegram.messenger.fluxgram.ReadTimeArchive;
import org.telegram.messenger.fluxgram.SavedAttachments;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.InviteMembersBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;

import java.util.ArrayList;
import java.util.HashSet;

public final class FluxGramStealthActivity extends BaseFragment {
    private final boolean[] checked = new boolean[5];
    private int storageLimit;
    private int statisticsGeneration;
    private boolean statisticsLoading;
    private boolean storageClearRunning;
    private LocalStorageStatistics.Result statistics;
    private UniversalRecyclerView list;

    @Override
    public void onFragmentDestroy() {
        statisticsGeneration++;
        list = null;
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        checked[4] = SavedAttachments.get(currentAccount).isEnabled();
        storageLimit = SavedAttachments.get(currentAccount).totalLimitIndex();
        checked[0] = EditHistory.keepsDeleted(currentAccount);
        checked[1] = EditHistory.isEnabled(currentAccount);
        checked[2] = EditHistory.includesBots(currentAccount);
        checked[3] = ReadTimeArchive.isEnabled(currentAccount);
        actionBar.setTitle(text(R.string.FluxGramStealth));
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

        list = new UniversalRecyclerView(
                this, (items, adapter) -> fillItems(items), null, null
        );
        list.setItemAnimator(null);
        list.adapter.setApplyBackground(false);
        FluxGramSettingsSections.apply(list, currentAccount);
        content.addView(list, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT
        ));
        actionBar.setAdaptiveBackground(list);
        loadStatistics();

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items) {
        addHeader(items, R.string.FluxGramStealthHistory);
        addSwitch(items, 0, R.string.FluxGramStealthDeleted, 0, true);
        addDeletedChats(items);
        addSwitch(items, 1, R.string.FluxGramStealthEdits, 0, false);
        items.add(UItem.asShadow(null));

        addSwitch(items, 2, R.string.FluxGramStealthBots, 0, true);
        TextSettingsCell clearHistory = new TextSettingsCell(getParentActivity(), getResourceProvider());
        clearHistory.setTextAndIcon(text(R.string.FluxGramEditHistoryClear), R.drawable.msg_clearcache, false);
        clearHistory.setBackground(Theme.getSelectorDrawable(false));
        clearHistory.setOnClickListener(view -> showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.FluxGramEditHistoryClear))
                .setMessage(text(R.string.FluxGramEditHistoryClearInfo))
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Delete), (dialog, which) -> EditHistory.clearVersions(currentAccount, success -> {
                    if (!isFinished && getParentActivity() != null) {
                        if (success) {
                            loadStatistics();
                        } else {
                            showError();
                        }
                    }
                }))
                .create()));
        items.add(UItem.asCustom(clearHistory, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asShadow(null));

        addSwitch(items, 3, R.string.FluxGramStealthReadDate, 0, false);
        items.add(UItem.asShadow(text(R.string.FluxGramStealthReadDateInfo)));

        addHeader(items, R.string.FluxGramStealthAttachments);
        addSwitch(items, 4, R.string.FluxGramStealthSaveAttachments,
                checked[0] ? R.string.FluxGramStealthAttachmentOptions
                        : R.string.FluxGramStealthAttachmentRequiresDeleted, true);

        SavedAttachments archive = SavedAttachments.get(currentAccount);
        TextSettingsCell chats = new TextSettingsCell(getParentActivity(), getResourceProvider());
        chats.setTextAndValue(text(R.string.FluxGramAttachmentChats), archive.allChats()
                ? text(R.string.FluxGramAttachmentAllChats)
                : LocaleController.formatString(R.string.FluxGramAttachmentSelectedChats, archive.selectedChats().size()), true);
        chats.setBackground(Theme.getSelectorDrawable(false));
        chats.setOnClickListener(view -> showChatSelection());
        items.add(UItem.asCustom(chats, LayoutHelper.WRAP_CONTENT));

        TextSettingsCell fileLimit = new TextSettingsCell(getParentActivity(), getResourceProvider());
        String[] limits = {"10 MB", "50 MB", "100 MB", "500 MB"};
        fileLimit.setTextAndValue(text(R.string.FluxGramAttachmentFileLimit), limits[archive.fileLimitIndex()], false);
        fileLimit.setBackground(Theme.getSelectorDrawable(false));
        fileLimit.setOnClickListener(view -> showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.FluxGramAttachmentFileLimit))
                .setItems(limits, (dialog, which) -> {
                    archive.setFileLimit(which);
                    list.adapter.update(false);
                })
                .create()));
        items.add(UItem.asCustom(fileLimit, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asShadow(null));

        addHeader(items, R.string.FluxGramStealthStorageLimit);
        SlideChooseView slider = new SlideChooseView(getParentActivity(), getResourceProvider());
        slider.setOptions(storageLimit, text(R.string.FluxGramStealthLimit300Mb),
                text(R.string.FluxGramStealthLimit1Gb), text(R.string.FluxGramStealthLimit2Gb),
                text(R.string.FluxGramStealthLimit5Gb), text(R.string.FluxGramStealthLimit16Gb), "∞");
        slider.setCallback(index -> {
            storageLimit = index;
            archive.setTotalLimit(index, success -> {
                if (!isFinished && list != null && getParentActivity() != null) {
                    loadStatistics();
                }
            });
        });
        items.add(UItem.asCustom(slider, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asShadow(text(R.string.FluxGramStealthStorageInfo)));

        TextSettingsCell clear = new TextSettingsCell(getParentActivity(), getResourceProvider());
        clear.setTextAndIcon(text(R.string.FluxGramAttachmentClear), R.drawable.msg_clearcache, false);
        clear.setBackground(Theme.getSelectorDrawable(false));
        clear.setOnClickListener(view -> showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.FluxGramAttachmentClear))
                .setMessage(text(R.string.FluxGramAttachmentClearInfo))
                .setNegativeButton(text(R.string.Cancel), null)
                .setPositiveButton(text(R.string.Delete), (dialog, which) -> archive.clearFiles(success -> {
                    if (!isFinished && list != null && getParentActivity() != null) {
                        loadStatistics();

                        if (!success) {
                            showError();
                        }
                    }
                }))
                .create()));
        items.add(UItem.asCustom(clear, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asShadow(null));

        addHeader(items, R.string.FluxGramStorageStatistics);
        addStatistic(items, R.string.FluxGramStorageEditVersions,
                countValue(statistics != null && statistics.editVersionsAvailable,
                        statistics == null ? 0 : statistics.editVersions), true);
        addStatistic(items, R.string.FluxGramStorageDeletedMessages,
                countValue(statistics != null && statistics.deletedMessagesAvailable,
                        statistics == null ? 0 : statistics.deletedMessages), true);
        addStatistic(items, R.string.FluxGramStorageAttachments,
                countValue(statistics != null && statistics.attachmentsAvailable,
                        statistics == null ? 0 : statistics.attachments), true);
        addStatistic(items, R.string.FluxGramStorageAttachmentSize,
                sizeValue(), true);
        addStatistic(items, R.string.FluxGramStorageAttachmentFailures,
                countValue(statistics != null && statistics.attachmentsAvailable,
                        statistics == null ? 0 : statistics.attachmentFailures), false);
        items.add(UItem.asShadow(null));

        addHeader(items, R.string.FluxGramStealthData);
        addAction(items, R.string.FluxGramStealthExport, R.drawable.msg_share, true);
        addAction(items, R.string.FluxGramStealthImport, R.drawable.msg_download, false);
        items.add(UItem.asShadow(null));
        addClearAction(items);
        items.add(UItem.asShadow(text(R.string.FluxGramStealthRetentionInfo)));
    }

    private void addHeader(ArrayList<UItem> items, int title) {
        HeaderCell header = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        header.setText(text(title));
        items.add(UItem.asCustomShadow(header, LayoutHelper.WRAP_CONTENT));
    }

    private void addDeletedChats(ArrayList<UItem> items) {
        TextSettingsCell cell = new TextSettingsCell(getParentActivity(), getResourceProvider());
        cell.setTextAndValue(text(R.string.FluxGramDeletedChats), EditHistory.keepsDeletedInAllChats(currentAccount)
                ? text(R.string.FluxGramAttachmentAllChats)
                : LocaleController.formatString(R.string.FluxGramAttachmentSelectedChats,
                        EditHistory.deletedChats(currentAccount).size()), true);
        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setEnabled(checked[0], null);

        if (checked[0]) {
            cell.setOnClickListener(view -> showDeletedChatSelection());
        }

        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
    }

    private void addSwitch(ArrayList<UItem> items, int index, int title, int description, boolean divider) {
        TextCheckCell cell = new TextCheckCell(getParentActivity(), getResourceProvider());
        boolean enabled = index != 4 || checked[0];

        if (description == 0) {
            cell.setTextAndCheck(text(title), checked[index], divider);
        } else {
            cell.setTextAndValueAndCheck(text(title), text(description), checked[index], true, divider);
        }

        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setEnabled(enabled, null);

        if (enabled) {
            cell.setOnClickListener(view -> {
                checked[index] = !checked[index];
                cell.setChecked(checked[index]);

                if (index == 0) {
                    EditHistory.setKeepsDeleted(currentAccount, checked[index]);
                    list.adapter.update(false);
                } else if (index == 1) {
                    EditHistory.setEnabled(currentAccount, checked[index]);
                } else if (index == 2) {
                    EditHistory.setIncludesBots(currentAccount, checked[index]);
                } else if (index == 3) {
                    ReadTimeArchive.setEnabled(currentAccount, checked[index]);
                } else if (index == 4) {
                    SavedAttachments.get(currentAccount).setEnabled(checked[index]);
                }
            });
        }

        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
    }

    private void addStatistic(ArrayList<UItem> items, int title, String value, boolean divider) {
        TextSettingsCell cell = new TextSettingsCell(getParentActivity(), getResourceProvider());
        cell.setTextAndValue(text(title), value, false, divider);
        cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
    }

    private String countValue(boolean available, long value) {
        if (statisticsLoading) {
            return text(R.string.Loading);
        }

        return available ? LocaleController.formatNumber(value, ' ') : "\u2014";
    }

    private String sizeValue() {
        if (statisticsLoading) {
            return text(R.string.Loading);
        }

        if (statistics == null || !statistics.attachmentsAvailable) {
            return "\u2014";
        }

        return AndroidUtilities.formatFileSize(statistics.attachmentBytes);
    }

    private void loadStatistics() {
        int generation = ++statisticsGeneration;
        int account = currentAccount;
        statistics = null;
        statisticsLoading = true;

        if (list != null) {
            list.adapter.update(false);
        }

        LocalStorageStatistics.load(account, result -> {
            if (generation != statisticsGeneration || isFinished || list == null || currentAccount != account) {
                return;
            }

            statistics = result;
            statisticsLoading = false;
            list.adapter.update(false);
        });
    }

    private void showError() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setMessage(text(R.string.ErrorOccurred))
                .setPositiveButton(text(R.string.OK), null)
                .create());
    }

    private void showChatSelection() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.FluxGramAttachmentChats))
                .setItems(new CharSequence[] {
                        text(R.string.FluxGramAttachmentAllChats), text(R.string.SelectChats)
                }, (dialog, which) -> {
                    if (which == 0) {
                        SavedAttachments.get(currentAccount).setChats(null);
                        list.adapter.update(false);
                        return;
                    }

                    Bundle args = new Bundle();
                    args.putBoolean("onlySelect", true);
                    args.putBoolean("checkCanWrite", false);
                    args.putBoolean("allowGlobalSearch", false);
                    args.putBoolean("allowSwitchAccount", false);
                    args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_WIDGET);
                    DialogsActivity picker = new DialogsActivity(args);
                    picker.setCurrentAccount(currentAccount);
                    picker.setDelegate((fragment, dialogs, message, param, notify, date, repeat, topics) -> {
                        HashSet<String> selected = new HashSet<>();

                        for (org.telegram.messenger.MessagesStorage.TopicKey key : dialogs) {
                            if (!org.telegram.messenger.DialogObject.isEncryptedDialog(key.dialogId)) {
                                selected.add(Long.toString(key.dialogId));
                            }
                        }

                        SavedAttachments.get(currentAccount).setChats(selected);
                        fragment.finishFragment();
                        list.adapter.update(false);
                        return true;
                    });
                    presentFragment(picker);
                })
                .create());
    }

    private void showDeletedChatSelection() {
        int account = currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.FluxGramDeletedChats))
                .setItems(new CharSequence[] {
                        text(R.string.FluxGramAttachmentAllChats), text(R.string.SelectChats)
                }, (dialog, which) -> {
                    if (!activeAccount(account, owner)) {
                        return;
                    }

                    if (which == 0) {
                        EditHistory.setDeletedChats(account, null);
                        list.adapter.update(false);
                        return;
                    }

                    ArrayList<Long> selected = availableDeletedChats(account);
                    InviteMembersBottomSheet picker = new InviteMembersBottomSheet(
                            getParentActivity(), account, null, 0, this, getResourceProvider()
                    );
                    picker.setDelegate(dialogs -> {
                        if (activeAccount(account, owner)) {
                            EditHistory.setDeletedChats(account, new HashSet<>(dialogs));
                            list.adapter.update(false);
                        }
                    }, selected);
                    picker.setSelectedContacts(selected);
                    showDialog(picker);
                })
                .create());
    }

    private ArrayList<Long> availableDeletedChats(int account) {
        ArrayList<Long> result = new ArrayList<>();
        MessagesController controller = MessagesController.getInstance(account);

        for (long dialog : EditHistory.deletedChats(account)) {
            if ((DialogObject.isUserDialog(dialog) && controller.getUser(dialog) != null)
                    || (DialogObject.isChatDialog(dialog) && controller.getChat(-dialog) != null)) {
                result.add(dialog);
            }
        }

        return result;
    }

    private boolean activeAccount(int account, long owner) {
        return !isFinished && list != null && getParentActivity() != null && currentAccount == account
                && owner != 0 && owner == UserConfig.getInstance(account).getClientUserId();
    }

    private void addAction(ArrayList<UItem> items, int title, int icon, boolean divider) {
        TextSettingsCell cell = new TextSettingsCell(getParentActivity(), getResourceProvider());
        cell.setTextAndIcon(text(title), icon, divider);
        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setOnClickListener(view -> showPlaceholder());
        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
    }

    private void addClearAction(ArrayList<UItem> items) {
        TextSettingsCell cell = new TextSettingsCell(getParentActivity(), getResourceProvider());
        cell.setTextAndIcon(text(R.string.FluxGramStealthClear), R.drawable.msg_clearcache, false);
        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setEnabled(!storageClearRunning, null);

        if (!storageClearRunning) {
            cell.setOnClickListener(view -> showDialog(new AlertDialog.Builder(
                    getParentActivity(), getResourceProvider())
                    .setTitle(text(R.string.FluxGramStealthClear))
                    .setMessage(text(R.string.FluxGramStealthClearInfo))
                    .setNegativeButton(text(R.string.Cancel), null)
                    .setPositiveButton(text(R.string.Delete), (dialog, which) -> clearSavedData())
                    .create()));
        }

        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
    }

    private void clearSavedData() {
        int account = currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();

        if (storageClearRunning || !activeAccount(account, owner)) {
            return;
        }

        storageClearRunning = true;
        list.adapter.update(false);
        LocalStorageCleanup.clear(account, success -> {
            if (!activeAccount(account, owner)) {
                return;
            }

            storageClearRunning = false;

            if (success) {
                loadStatistics();
            } else {
                list.adapter.update(false);
                showError();
            }
        });
    }

    private void showPlaceholder() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.FluxGramStealth))
                .setMessage(text(R.string.FluxGramStealthComingSoon))
                .setPositiveButton(text(R.string.OK), null)
                .create());
    }

    private String text(int resource) {
        return LocaleController.getString(resource);
    }
}
