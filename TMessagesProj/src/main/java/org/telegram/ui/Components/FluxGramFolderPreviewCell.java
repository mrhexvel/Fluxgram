package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.SparseArray;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.FolderSettings;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.fluxgram.FolderPresentation;
import org.telegram.ui.fluxgram.FolderUnreadCounter;

import java.util.ArrayList;

public final class FluxGramFolderPreviewCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final int account;
    private final SparseArray<MessagesController.DialogFilter> filtersByTab = new SparseArray<>();
    private final FilterTabsView tabsView;

    public FluxGramFolderPreviewCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);
        this.account = account;

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        tabsView = new FilterTabsView(context, resourcesProvider) {
            @Override
            protected CharSequence getDefaultTabTitle(boolean compact) {
                return FolderPresentation.createTitle(
                        LocaleController.getString(compact
                                ? R.string.FilterAllChatsShort
                                : R.string.FilterAllChats),
                        null,
                        FolderSettings.getPolicy(account)
                );
            }
        };
        tabsView.setPadding(0, dp(7), 0, dp(7));

        BlurredBackgroundSourceColor backgroundSource = new BlurredBackgroundSourceColor();
        backgroundSource.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        BlurredBackgroundDrawable background = new BlurredBackgroundDrawableViewFactory(backgroundSource)
                .create(tabsView, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
        background.setRadius(dp(18));
        background.setPadding(dp(6.666f));
        tabsView.setBlurredBackground(background);

        tabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
            }

            @Override
            public void onPageScrolled(float progress) {
            }

            @Override
            public void onSamePageSelected() {
            }

            @Override
            public int getTabCounter(int tabId) {
                return FolderUnreadCounter.resolve(
                        account,
                        filtersByTab.get(tabId),
                        FolderSettings.getPolicy(account)
                );
            }

            @Override
            public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                return false;
            }

            @Override
            public boolean isTabMenuVisible() {
                return false;
            }

            @Override
            public void onDeletePressed(int id) {
            }

            @Override
            public void onPageReorder(int fromId, int toId) {
            }

            @Override
            public boolean canPerformActions() {
                return false;
            }
        });
        addView(tabsView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                50,
                Gravity.TOP,
                4,
                4,
                4,
                0
        ));

        updateFolders();
    }

    public void updateFolders() {
        ArrayList<MessagesController.DialogFilter> filters = getVisibleFolders();
        if (hasSameFolders(filters)) {
            updateExistingFolders(filters, false);
        } else {
            rebuildFolders(filters, false);
        }
    }

    public void updateFolders(boolean animated) {
        ArrayList<MessagesController.DialogFilter> filters = getVisibleFolders();
        if (hasSameFolders(filters)) {
            updateExistingFolders(filters, animated);
        } else {
            rebuildFolders(filters, animated);
        }
    }

    private ArrayList<MessagesController.DialogFilter> getVisibleFolders() {
        ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(account).getDialogFilters();
        FolderSettings.Policy policy = FolderSettings.getPolicy(account);
        if (FolderSettings.isAllChatsVisible(policy, filters)) {
            return filters;
        }

        ArrayList<MessagesController.DialogFilter> visible = new ArrayList<>(filters.size() - 1);
        for (int index = 0; index < filters.size(); index++) {
            MessagesController.DialogFilter filter = filters.get(index);
            if (!filter.isDefault()) {
                visible.add(filter);
            }
        }

        return visible;
    }

    private boolean hasSameFolders(ArrayList<MessagesController.DialogFilter> filters) {
        if (tabsView.getTabsCount() != filters.size()) {
            return false;
        }

        for (int index = 0; index < filters.size(); index++) {
            if (tabsView.getStableId(index) != filters.get(index).localId) {
                return false;
            }
        }

        return true;
    }

    private void updateExistingFolders(
            ArrayList<MessagesController.DialogFilter> filters,
            boolean animated
    ) {
        FolderSettings.Policy policy = FolderSettings.getPolicy(account);

        filtersByTab.clear();
        for (int index = 0; index < filters.size(); index++) {
            MessagesController.DialogFilter filter = filters.get(index);
            filtersByTab.put(index, filter);
            tabsView.getTab(index).setTitle(createTitle(filter, policy), filter.title_noanimate);
        }

        tabsView.refreshTabs(animated);
    }

    private void rebuildFolders(
            ArrayList<MessagesController.DialogFilter> filters,
            boolean animated
    ) {
        FolderSettings.Policy policy = FolderSettings.getPolicy(account);

        tabsView.removeTabs();
        tabsView.resetTabId();
        filtersByTab.clear();

        for (int index = 0; index < filters.size(); index++) {
            MessagesController.DialogFilter filter = filters.get(index);

            filtersByTab.put(index, filter);
            tabsView.addTab(
                    index,
                    filter.localId,
                    createTitle(filter, policy),
                    filter.title_noanimate,
                    filter.isDefault(),
                    filter.locked
            );
        }

        tabsView.finishAddingTabs(animated);
    }

    private CharSequence createTitle(
            MessagesController.DialogFilter filter,
            FolderSettings.Policy policy
    ) {
        if (filter.isDefault()) {
            return FolderPresentation.createTitle(
                    LocaleController.getString(R.string.FilterAllChats),
                    null,
                    policy
            );
        }

        CharSequence title = tabsView.text(filter.name, filter.entities);
        return FolderPresentation.createTitle(title, filter, policy);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.dialogFiltersUpdated);
        center.addObserver(this, NotificationCenter.dialogsNeedReload);
        center.addObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    protected void onDetachedFromWindow() {
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.removeObserver(this, NotificationCenter.dialogFiltersUpdated);
        center.removeObserver(this, NotificationCenter.dialogsNeedReload);
        center.removeObserver(this, NotificationCenter.updateInterfaces);
        super.onDetachedFromWindow();
    }

    @Override
    public void didReceivedNotification(int id, int notificationAccount, Object... args) {
        if (id == NotificationCenter.dialogFiltersUpdated) {
            updateFolders();
        } else {
            tabsView.checkTabsCounter();
        }
    }
}
