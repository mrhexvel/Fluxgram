package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
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

public final class FluxGramStealthActivity extends BaseFragment {
    private final boolean[] checked = new boolean[5];
    private int storageLimit = 2;

    @Override
    public View createView(Context context) {
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

        UniversalRecyclerView list = new UniversalRecyclerView(
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

        TextSettingsCell folder = new TextSettingsCell(getParentActivity(), getResourceProvider());
        folder.setTextAndValue(text(R.string.FluxGramStealthFolder), "Saved Attachments", false);
        folder.setBackground(Theme.getSelectorDrawable(false));
        folder.setOnClickListener(view -> showPlaceholder());
        items.add(UItem.asCustom(folder, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asShadow(null));

        addHeader(items, R.string.FluxGramStealthStorageLimit);
        SlideChooseView slider = new SlideChooseView(getParentActivity(), getResourceProvider());
        slider.setOptions(storageLimit, text(R.string.FluxGramStealthLimit300Mb),
                text(R.string.FluxGramStealthLimit1Gb), text(R.string.FluxGramStealthLimit2Gb),
                text(R.string.FluxGramStealthLimit5Gb), text(R.string.FluxGramStealthLimit16Gb), "∞");
        slider.setCallback(index -> storageLimit = index);
        items.add(UItem.asCustom(slider, LayoutHelper.WRAP_CONTENT));
        items.add(UItem.asShadow(text(R.string.FluxGramStealthStorageInfo)));

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
        });
        items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
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
