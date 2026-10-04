package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

public class FluxGramSettingsActivity extends BaseFragment {
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
            PluginManager manager = PluginManager.getInstance();

            items.add(UItem.asHeader(
                    LocaleController.getString(R.string.FluxGramPlugins)
            ));

            int rowId = 1;

            for (Plugin plugin : manager.getPlugins()) {
                UItem row = UItem.asSwitch(rowId++, plugin.getName())
                        .setChecked(manager.isEnabled(plugin.getId()));

                row.object = plugin.getId();
                items.add(row);

                items.add(UItem.asShadow(
                        plugin.getDescription() + "\n\nv" + plugin.getVersion()
                ));
            }

        }, (item, view, position, x, y) -> {
            if (!(item.object instanceof String)) {
                return;
            }

            String pluginId = (String) item.object;
            PluginManager manager = PluginManager.getInstance();

            boolean enabled = manager.isEnabled(pluginId);
            manager.setEnabled(pluginId, !enabled);

            listView.adapter.update(true);
        }, null);

        listView.adapter.setApplyBackground(false);
        listView.setSections(dp(12), dp(20), false);

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
