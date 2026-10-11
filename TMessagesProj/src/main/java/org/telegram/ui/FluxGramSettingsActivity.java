package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.FileProvider;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.FluxGramPluginCard;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.ExternalPlugins;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.messenger.plugins.PythonPluginAdapter;
import org.telegram.messenger.plugins.packages.PluginManifest;
import org.telegram.messenger.plugins.packages.PluginPackageStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.FluxGramSettingsHomeView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class FluxGramSettingsActivity extends BaseFragment {
    private static final int HOME = 0;
    private static final int GENERAL = 1;
    private static final int PLUGINS = 2;

    private final int section;
    private UniversalRecyclerView listView;
    private boolean destroyed;

    private final Runnable refresh = () -> {
        if (listView != null && !destroyed) {
            listView.adapter.update(false);
        }
    };

    public FluxGramSettingsActivity() {
        this(HOME);
    }

    private FluxGramSettingsActivity(int section) {
        this.section = section;
    }

    @Override
    public boolean onFragmentCreate() {
        ExternalPlugins.addListener(refresh);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        ExternalPlugins.removeListener(refresh);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        int title = R.string.FluxGramSettings;

        if (section == GENERAL) {
            title = R.string.FluxGramGeneral;
        } else if (section == PLUGINS) {
            title = R.string.FluxGramPlugins;
        }

        actionBar.setTitle(section == HOME ? "" : LocaleController.getString(title));
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
                (items, adapter) -> fillItems(items),
                null,
                null
        );

        listView.adapter.setApplyBackground(false);
        if (section == PLUGINS) {
            listView.setItemAnimator(null);
        }

        if (section == GENERAL) {
            listView.setSections(dp(12), dp(20), false);
        }

        content.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));

        actionBar.setAdaptiveBackground(listView);

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items) {
        if (section == HOME) {
            FluxGramSettingsHomeView home = new FluxGramSettingsHomeView(
                    getParentActivity(),
                    getResourceProvider(),
                    PluginManager.getInstance().isSystemEnabled(),
                    () -> presentFragment(new FluxGramSettingsActivity(GENERAL)),
                    () -> presentFragment(new FluxGramSettingsActivity(PLUGINS)),
                    () -> presentFragment(new FluxGramGhostModeActivity()),
                    () -> presentFragment(new FluxGramStealthActivity())
            );
            items.add(UItem.asCustom(home, LayoutHelper.WRAP_CONTENT));

            return;
        }

        if (section == GENERAL) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramGeneralEmpty)));

            return;
        }

        PluginManager manager = PluginManager.getInstance();
        boolean systemEnabled = manager.isSystemEnabled();
        FluxGramPluginCard systemCard = new FluxGramPluginCard(
                getParentActivity(), getResourceProvider(), null,
                LocaleController.getString(R.string.FluxGramPluginSystem),
                LocaleController.getString(systemEnabled
                        ? R.string.FluxGramPluginsEnabled : R.string.FluxGramPluginsDisabled),
                null, systemEnabled, true,
                () -> {
                    manager.setSystemEnabled(!manager.isSystemEnabled());
                    ExternalPlugins.changed();
                }, null, null, null);
        items.add(UItem.asCustom(systemCard, LayoutHelper.WRAP_CONTENT));

        if (!systemEnabled) {
            return;
        }

        if (ExternalPlugins.getStartupError() != null) {
            items.add(UItem.asShadow(
                    LocaleController.getString(R.string.FluxGramPluginError)
                            + "\n" + ExternalPlugins.getStartupError()));
        }

        for (boolean external : new boolean[] {false, true}) {
            boolean headerAdded = false;
            ArrayList<Plugin> plugins = manager.getPlugins();

            if (external) {
                plugins.addAll(ExternalPlugins.getIncompatiblePlugins());
            }

            for (Plugin plugin : plugins) {
                boolean isExternal = ExternalPlugins.isExternal(plugin);

                if (isExternal != external) {
                    continue;
                }

                if (!headerAdded) {
                    items.add(UItem.asHeader(LocaleController.getString(external
                            ? R.string.FluxGramInstalledPlugins : R.string.FluxGramBuiltInPlugins)));
                    headerAdded = true;
                }

                PluginPackageStore.Pending pending = ExternalPlugins.getPending(plugin.getId());
                String description = plugin.getDescription();
                boolean incompatible = plugin instanceof ExternalPlugins.IncompatiblePlugin;

                if (pending != null) {
                    description += "\n\n" + LocaleController.getString(pending.delete
                            ? R.string.FluxGramPluginDeletionPending : R.string.FluxGramPluginUpdatePending);
                }

                if (plugin instanceof PythonPluginAdapter
                        && ((PythonPluginAdapter) plugin).getLoadError() != null) {
                    description += "\n\n" + LocaleController.getString(R.string.FluxGramPluginLoadFailed);
                }

                if (incompatible) {
                    PluginManifest manifest =
                            ((ExternalPlugins.IncompatiblePlugin) plugin).getManifest();
                    if (manifest.javaModule != null && !manifest.javaModule.isCompatible()) {
                        description += "\n\n" + LocaleController.formatString(
                                "FluxGramPluginJavaIncompatible",
                                R.string.FluxGramPluginJavaIncompatible,
                                manifest.javaModule.apiVersion,
                                manifest.javaModule.minApiMinor,
                                PluginManifest.JAVA_API_VERSION,
                                PluginManifest.JAVA_API_MINOR_VERSION);
                    } else {
                        description += "\n\n" + LocaleController.formatString(
                            "FluxGramPluginIncompatible",
                            R.string.FluxGramPluginIncompatible,
                            manifest.sdkVersion,
                            manifest.minSdkMinor,
                            PluginManifest.SDK_VERSION,
                            PluginManifest.SDK_MINOR_VERSION);
                    }
                }

                FluxGramPluginCard card = new FluxGramPluginCard(
                        getParentActivity(), getResourceProvider(), plugin, plugin.getName(),
                        LocaleController.getString(R.string.FluxGramPluginVersion) + " " + plugin.getVersion(),
                        description, !incompatible && manager.isEnabled(plugin.getId()),
                        !incompatible && pending == null && !ExternalPlugins.isRemoving(plugin.getId())
                                && !(plugin instanceof PythonPluginAdapter
                                && ((PythonPluginAdapter) plugin).isStoppingForRemoval()),
                        incompatible ? null
                                : () -> manager.setEnabled(
                                        plugin.getId(), !manager.isEnabled(plugin.getId())),
                        () -> sharePlugin(plugin),
                        incompatible ? null
                                : () -> presentFragment(new PluginSettingsActivity(plugin.getId())),
                        external && pending == null && !ExternalPlugins.isRemoving(plugin.getId())
                                ? () -> confirmDelete(plugin) : null);
                items.add(UItem.asCustom(card, LayoutHelper.WRAP_CONTENT));
            }
        }
    }

    private void showInfo(String title, String message) {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create());
    }

    private boolean preparingShare;

    private void sharePlugin(Plugin plugin) {
        if (!ExternalPlugins.isExternal(plugin)) {
            showInfo(plugin.getName(), LocaleController.getString(R.string.FluxGramBuiltInShareInfo));
            return;
        }

        if (preparingShare) {
            return;
        }

        preparingShare = true;
        ExternalPlugins.export(getParentActivity(), plugin.getId(), (file, error) -> {
            preparingShare = false;

            if (destroyed || getParentActivity() == null) {
                return;
            }

            if (error != null) {
                showError(error);
                return;
            }

            try {
                Intent intent = new Intent(getParentActivity(), LaunchActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                        getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file));
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getParentActivity().startActivity(intent);
            } catch (Exception failure) {
                showError(failure);
            }
        });
    }

    private void showError(Exception error) {
        if (destroyed || getParentActivity() == null) {
            return;
        }

        showDialog(
                new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(LocaleController.getString(R.string.FluxGramPluginError))
                        .setMessage(error.getMessage())
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .create());
    }

    private void confirmDelete(Plugin plugin) {
        showDialog(
                new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(LocaleController.formatString(
                                "FluxGramDeletePluginConfirm", R.string.FluxGramDeletePluginConfirm,
                                plugin.getName()))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .setPositiveButton(
                                LocaleController.getString(R.string.Delete),
                                (dialog, which) -> {
                                    ExternalPlugins.delete(
                                            plugin.getId(),
                                            (id, error) -> {
                                                if (destroyed || getParentActivity() == null) {
                                                    return;
                                                }

                                                if (error != null) {
                                                    showError(error);
                                                } else {
                                                    refresh.run();
                                                }
                                            });
                                })
                        .create());
    }

    private void showRestartInfo() {
        showDialog(
                new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(
                                LocaleController.getString(R.string.FluxGramPluginRestartRequired))
                        .setMessage(LocaleController.getString(R.string.FluxGramPluginRestartInfo))
                        .setNegativeButton(LocaleController.getString(R.string.OK), null)
                        .setPositiveButton(
                                LocaleController.getString(R.string.FluxGramOpenAndroidSettings),
                                (dialog, which) ->
                                        getParentActivity()
                                                .startActivity(
                                                        new Intent(
                                                                android.provider.Settings
                                                                        .ACTION_APPLICATION_DETAILS_SETTINGS,
                                                                android.net.Uri.parse(
                                                                        "package:"
                                                                                + getParentActivity()
                                                                                        .getPackageName()))))
                        .create());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (listView != null) {
            listView.adapter.update(false);
        }
    }
}
