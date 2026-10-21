package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.MainMenuPolicy;
import org.telegram.messenger.fluxgram.MainMenuSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.FluxGramMainMenuCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FluxGramMainMenuSettingsActivity extends BaseFragment {
    private static final int RESET = 1;

    private UniversalRecyclerView listView;
    private int orderSection;

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.FluxGramMainMenu));
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

        listView = new UniversalRecyclerView(
                this,
                this::fillItems,
                this::onItemClick,
                null
        );
        listView.adapter.setApplyBackground(false);
        listView.listenReorder(this::onReordered);
        listView.setReorderLongPressEnabled(false);
        listView.allowReorder(true);
        FluxGramSettingsSections.apply(listView, currentAccount);
        content.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));

        actionBar.setAdaptiveBackground(listView);

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        addSectionLabel(items, R.string.FluxGramMainMenu);

        MainMenuPolicy.State state = MainMenuSettings.getState();
        List<String> order = state.getOrder();
        orderSection = adapter.reorderSectionStart();
        for (int index = 0; index < order.size(); index++) {
            String id = order.get(index);
            items.add(FluxGramMainMenuCell.Factory.of(
                    id,
                    itemTitle(id),
                    !state.getHidden().contains(id),
                    MainMenuPolicy.isDivider(id),
                    index > 0,
                    index + 1 < order.size(),
                    direction -> moveItem(id, direction)
            ));
        }
        adapter.reorderSectionEnd();

        items.add(UItem.asButton(
                RESET,
                R.drawable.msg_reset,
                LocaleController.getString(R.string.Reset)
        ));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramMainMenuInfo)));
    }

    private void addSectionLabel(ArrayList<UItem> items, int textResource) {
        HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        label.setText(LocaleController.getString(textResource));
        items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == RESET) {
            confirmReset();
            return;
        }
        if (!item.instanceOf(FluxGramMainMenuCell.Factory.class)) {
            return;
        }

        String id = (String) item.object;
        if (MainMenuPolicy.isDivider(id)) {
            return;
        }

        MainMenuPolicy.State state = MainMenuSettings.getState();
        MainMenuSettings.setCommandVisible(id, state.getHidden().contains(id));
        listView.adapter.update(true);
    }

    private void onReordered(int sectionId, ArrayList<UItem> items) {
        if (sectionId != orderSection) {
            return;
        }

        ArrayList<String> order = new ArrayList<>();
        for (UItem item : items) {
            if (item.object instanceof String) {
                order.add((String) item.object);
            }
        }

        Set<String> known = new HashSet<>(MainMenuPolicy.knownIds());
        if (order.size() == known.size() && known.equals(new HashSet<>(order))) {
            MainMenuSettings.setOrder(order);
        }
    }

    private void moveItem(String id, int direction) {
        ArrayList<String> order = new ArrayList<>(MainMenuSettings.getState().getOrder());
        int index = order.indexOf(id);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }

        order.remove(index);
        order.add(target, id);
        MainMenuSettings.setOrder(order);
        listView.adapter.update(true);
    }

    private void confirmReset() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramMainMenu))
                .setMessage(LocaleController.getString(R.string.FluxGramMainMenuResetConfirm))
                .setPositiveButton(LocaleController.getString(R.string.Reset), (dialog, which) -> {
                    MainMenuSettings.reset();
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private CharSequence itemTitle(String id) {
        if (MainMenuPolicy.THEME.equals(id)) {
            return LocaleController.getString(R.string.Theme);
        }
        if (MainMenuPolicy.NEW_GROUP.equals(id)) {
            return LocaleController.getString(R.string.NewGroup);
        }
        if (MainMenuPolicy.NEW_CHANNEL.equals(id)) {
            return LocaleController.getString(R.string.NewChannel);
        }
        if (MainMenuPolicy.SAVED_MESSAGES.equals(id)) {
            return LocaleController.getString(R.string.SavedMessages);
        }
        if (MainMenuPolicy.CONTACTS.equals(id)) {
            return LocaleController.getString(R.string.Contacts);
        }
        if (MainMenuPolicy.CALLS.equals(id)) {
            return LocaleController.getString(R.string.Calls);
        }
        if (MainMenuPolicy.WALLET.equals(id)) {
            return LocaleController.getString(R.string.WalletAttachMoney);
        }
        if (MainMenuPolicy.ACCOUNTS.equals(id)) {
            return LocaleController.getString(R.string.SettingsAccounts);
        }
        if (MainMenuPolicy.SETTINGS.equals(id)) {
            return LocaleController.getString(R.string.Settings);
        }
        if (MainMenuPolicy.PROXY.equals(id)) {
            return LocaleController.getString(R.string.MenuProxyTitle);
        }
        if (MainMenuPolicy.DIVIDER_PRIMARY.equals(id)) {
            return LocaleController.getString(R.string.FluxGramMainMenuPrimaryDivider);
        }

        return LocaleController.getString(R.string.FluxGramMainMenuSystemDivider);
    }
}
