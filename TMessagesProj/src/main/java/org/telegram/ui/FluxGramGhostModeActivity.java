package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.GhostModeSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public final class FluxGramGhostModeActivity extends BaseFragment {
    private static final int ENABLED = 1;
    private static final int HIDE_ONLINE = 2;
    private static final int HIDE_TYPING = 3;
    private static final int HIDE_READ_RECEIPTS = 4;
    private static final int HIDE_STORY_VIEWS = 5;

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
        listView.setSections();
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
        int selected = (hideOnline ? 1 : 0) + (hideTyping ? 1 : 0)
                + (hideReadReceipts ? 1 : 0) + (hideStoryViews ? 1 : 0);

        items.add(UItem.asHeader(text(R.string.FluxGramGhostMode)));
        items.add(UItem.asExpandableSwitch(
                        ENABLED, text(R.string.FluxGramGhostMode), selected + "/4"
                )
                .setChecked(enabled)
                .setCollapsed(!expanded)
                .setClickCallback(view -> {
                    GhostModeSettings.setEnabled(currentAccount, !GhostModeSettings.isEnabled(currentAccount));
                    listView.adapter.update(true);
                }));

        if (expanded) {
            items.add(UItem.asCheck(HIDE_ONLINE, text(R.string.FluxGramGhostModeHideOnline))
                    .setChecked(hideOnline).setEnabled(enabled));
            items.add(UItem.asCheck(HIDE_TYPING, text(R.string.FluxGramGhostModeHideTyping))
                    .setChecked(hideTyping).setEnabled(enabled));
            items.add(UItem.asCheck(HIDE_READ_RECEIPTS, text(R.string.FluxGramGhostModeHideReadReceipts))
                    .setChecked(hideReadReceipts).setEnabled(enabled));
            items.add(UItem.asCheck(HIDE_STORY_VIEWS, text(R.string.FluxGramGhostModeHideStoryViews))
                    .setChecked(hideStoryViews).setEnabled(enabled));
        }

        items.add(UItem.asShadow(text(enabled
                ? R.string.FluxGramGhostModePreviewInfo
                : R.string.FluxGramGhostModeDisabledInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ENABLED) {
            expanded = !expanded;
            listView.adapter.update(true);
            return;
        }

        if (!GhostModeSettings.isEnabled(currentAccount)) {
            return;
        }

        boolean checked = !item.checked;

        if (item.id == HIDE_ONLINE) {
            GhostModeSettings.setHidesOnline(currentAccount, checked);
        } else if (item.id == HIDE_TYPING) {
            GhostModeSettings.setHidesTyping(currentAccount, checked);
        } else if (item.id == HIDE_READ_RECEIPTS) {
            GhostModeSettings.setHidesReadReceipts(currentAccount, checked);
        } else if (item.id == HIDE_STORY_VIEWS) {
            GhostModeSettings.setHidesStoryViews(currentAccount, checked);
        } else {
            return;
        }

        ((TextCheckCell) view).setChecked(checked);
        listView.adapter.update(true);
    }

    private String text(int resource) {
        return LocaleController.getString(resource);
    }
}
