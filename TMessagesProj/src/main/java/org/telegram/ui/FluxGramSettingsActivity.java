package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.LoggingPlugin;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

public class FluxGramSettingsActivity extends BaseFragment {
    private static final int LOGGING = 1;
    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.FluxGramSettings));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = new FrameLayout(context);
        fragmentView = content;

        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        listView = new UniversalRecyclerView(this, (items, adapter) -> {
            items.add(UItem.asHeader(LocaleController.getString(R.string.FluxGramPlugins)));
            items.add(UItem.asSwitch(LOGGING, LocaleController.getString(R.string.FluxGramLogging))
                    .setChecked(PluginManager.getInstance().isEnabled(LoggingPlugin.ID)));
            items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramLoggingInfo)));
        }, (item, view, position, x, y) -> {
            if (item.id == LOGGING) {
                PluginManager manager = PluginManager.getInstance();
                manager.setEnabled(LoggingPlugin.ID, !manager.isEnabled(LoggingPlugin.ID));
                listView.adapter.update(true);
            }
        }, null);

        listView.adapter.setApplyBackground(false);
        listView.setSections(AndroidUtilities.dp(12), AndroidUtilities.dp(20), false);

        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar.setAdaptiveBackground(listView);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) listView.adapter.update(false);
    }
}
