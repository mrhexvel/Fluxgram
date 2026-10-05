package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.SavedAttachments;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;

public final class FluxGramStealthActivity extends BaseFragment {
    private final boolean[] checked = new boolean[5];
    private int storageLimit;
    private UniversalRecyclerView list;

    @Override
    public View createView(Context context) {
        checked[4] = SavedAttachments.get(currentAccount).isEnabled();
        storageLimit = SavedAttachments.get(currentAccount).totalLimitIndex();
        checked[0] = org.telegram.messenger.fluxgram.EditHistory.keepsDeleted(currentAccount);
        checked[1] = org.telegram.messenger.fluxgram.EditHistory.isEnabled(currentAccount);
        checked[2] = org.telegram.messenger.fluxgram.EditHistory.includesBots(currentAccount);
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
        list.setSections(AndroidUtilities.dp(12), AndroidUtilities.dp(20), false);
        content.addView(list, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT
        ));
        actionBar.setAdaptiveBackground(list);

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items) {
        items.add(UItem.asShadow(text(R.string.FluxGramStealthPreview)));
        addHeader(items, R.string.FluxGramStealthHistory);
        addSwitch(items, 0, R.string.FluxGramStealthDeleted, 0, true);
        addSwitch(items, 1, R.string.FluxGramStealthEdits, 0, false);
        items.add(UItem.asShadow(null));

        addSwitch(items, 2, R.string.FluxGramStealthBots, 0, false);
        items.add(UItem.asShadow(null));

        addSwitch(items, 3, R.string.FluxGramStealthReadDate, 0, false);
        items.add(UItem.asShadow(text(R.string.FluxGramStealthReadDateInfo)));

        addHeader(items, R.string.FluxGramStealthAttachments);
        addSwitch(items, 4, R.string.FluxGramStealthSaveAttachments,
                R.string.FluxGramStealthAttachmentOptions, true);

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
        items.add(UItem.asShadow(text(R.string.FluxGramAttachmentInfo)));

        if (archive.failureCount() > 0) {
            items.add(UItem.asShadow(LocaleController.formatString(R.string.FluxGramAttachmentFailures, archive.failureCount())));
        }

        addHeader(items, R.string.FluxGramStealthStorageLimit);
        SlideChooseView slider = new SlideChooseView(getParentActivity(), getResourceProvider());
        slider.setOptions(storageLimit, text(R.string.FluxGramStealthLimit300Mb),
                text(R.string.FluxGramStealthLimit1Gb), text(R.string.FluxGramStealthLimit2Gb),
                text(R.string.FluxGramStealthLimit5Gb), text(R.string.FluxGramStealthLimit16Gb), "∞");
        slider.setCallback(index -> {
            storageLimit = index;
            archive.setTotalLimit(index);
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
                        list.adapter.update(false);

                        if (!success) {
                            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                                    .setMessage(text(R.string.ErrorOccurred))
                                    .setPositiveButton(text(R.string.OK), null)
                                    .create());
                        }
                    }
                }))
                .create()));
        items.add(UItem.asCustom(clear, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asShadow(null));

        addHeader(items, R.string.FluxGramStealthData);
        addAction(items, R.string.FluxGramStealthExport, R.drawable.msg_share, true);
        addAction(items, R.string.FluxGramStealthImport, R.drawable.msg_download, false);
        items.add(UItem.asShadow(null));
        addAction(items, R.string.FluxGramStealthClear, R.drawable.msg_clearcache, false);
        items.add(UItem.asShadow(null));
    }

    private void addHeader(ArrayList<UItem> items, int title) {
        HeaderCell header = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        header.setText(text(title));
        items.add(UItem.asCustomShadow(header, LayoutHelper.WRAP_CONTENT));
    }

    private void addSwitch(ArrayList<UItem> items, int index, int title, int description, boolean divider) {
        TextCheckCell cell = new TextCheckCell(getParentActivity(), getResourceProvider());

        if (description == 0) {
            cell.setTextAndCheck(text(title), checked[index], divider);
        } else {
            cell.setTextAndValueAndCheck(text(title), text(description), checked[index], true, divider);
        }

        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setOnClickListener(view -> {
            checked[index] = !checked[index];
            cell.setChecked(checked[index]);

            if (index == 0) {
                org.telegram.messenger.fluxgram.EditHistory.setKeepsDeleted(currentAccount, checked[index]);
            } else if (index == 1) {
                org.telegram.messenger.fluxgram.EditHistory.setEnabled(currentAccount, checked[index]);
            } else if (index == 2) {
                org.telegram.messenger.fluxgram.EditHistory.setIncludesBots(currentAccount, checked[index]);
            } else if (index == 4) {
                SavedAttachments.get(currentAccount).setEnabled(checked[index]);
            }
        });
        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
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

    private void addAction(ArrayList<UItem> items, int title, int icon, boolean divider) {
        TextSettingsCell cell = new TextSettingsCell(getParentActivity(), getResourceProvider());
        cell.setTextAndIcon(text(title), icon, divider);
        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setOnClickListener(view -> showPlaceholder());
        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
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
