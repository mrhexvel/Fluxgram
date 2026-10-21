package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.MessageActionExecutor;
import org.telegram.messenger.fluxgram.MessageGestureSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.FluxGramSwipeActionCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FluxGramMessageSwipeSettingsActivity extends BaseFragment {
    private UniversalRecyclerView listView;
    private int actionsSection;

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.FluxGramSwipeActions));
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
        HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        label.setText(LocaleController.getString(R.string.FluxGramSwipeActions));
        items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));

        List<String> activeActions = MessageGestureSettings.getState().getSwipeActions();
        List<String> publishedActions = MessageGestureSettings.publishedSwipeActions();
        ArrayList<String> displayedActions = new ArrayList<>(activeActions);
        for (String actionId : publishedActions) {
            if (!displayedActions.contains(actionId)) {
                displayedActions.add(actionId);
            }
        }
        actionsSection = adapter.reorderSectionStart();
        for (int index = 0; index < displayedActions.size(); index++) {
            String actionId = displayedActions.get(index);
            items.add(FluxGramSwipeActionCell.Factory.of(
                    actionId,
                    actionTitle(actionId),
                    activeActions.contains(actionId),
                    index > 0,
                    index + 1 < displayedActions.size(),
                    direction -> moveItem(actionId, direction)
            ));
        }
        adapter.reorderSectionEnd();

        items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramSwipeActionsInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (!item.instanceOf(FluxGramSwipeActionCell.Factory.class)) {
            return;
        }

        String actionId = (String) item.object;
        ArrayList<String> activeActions = new ArrayList<>(
                MessageGestureSettings.getState().getSwipeActions()
        );
        if (activeActions.contains(actionId)) {
            if (activeActions.size() == 1) {
                return;
            }
            activeActions.remove(actionId);
        } else {
            activeActions.add(actionId);
        }

        MessageGestureSettings.setSwipeActions(activeActions);
        listView.adapter.update(true);
    }

    private void onReordered(int sectionId, ArrayList<UItem> items) {
        if (sectionId != actionsSection) {
            return;
        }

        Set<String> published = new HashSet<>(MessageGestureSettings.publishedSwipeActions());
        ArrayList<String> activeActions = new ArrayList<>();
        for (UItem item : items) {
            if (item.checked && item.object instanceof String && published.contains(item.object)) {
                activeActions.add((String) item.object);
            }
        }
        if (!activeActions.isEmpty()) {
            MessageGestureSettings.setSwipeActions(activeActions);
        }
    }

    private void moveItem(String actionId, int direction) {
        ArrayList<String> order = new ArrayList<>(
                MessageGestureSettings.getState().getSwipeActions()
        );
        for (String publishedAction : MessageGestureSettings.publishedSwipeActions()) {
            if (!order.contains(publishedAction)) {
                order.add(publishedAction);
            }
        }
        int index = order.indexOf(actionId);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }

        order.remove(index);
        order.add(target, actionId);
        Set<String> active = new HashSet<>(MessageGestureSettings.getState().getSwipeActions());
        ArrayList<String> activeOrder = new ArrayList<>();
        for (String id : order) {
            if (active.contains(id)) {
                activeOrder.add(id);
            }
        }
        MessageGestureSettings.setSwipeActions(activeOrder);
        listView.adapter.update(true);
    }

    private CharSequence actionTitle(String actionId) {
        if (MessageActionExecutor.REPLY.equals(actionId)) {
            return LocaleController.getString(R.string.Reply);
        }

        return actionId;
    }
}
