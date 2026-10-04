package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import org.telegram.ui.Components.FluxGramPluginIconView;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.ExternalPlugins;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.messenger.plugins.packages.PluginManifest;
import org.telegram.messenger.plugins.packages.PluginPackageStore;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class PluginInstallSheet {
    private static final Set<BaseFragment> reviewing = Collections.newSetFromMap(new WeakHashMap<>());
    private final BaseFragment fragment;

    private PluginInstallSheet(BaseFragment fragment) {
        this.fragment = fragment;
    }

    public static boolean isPluginFile(String name) {
        return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".fgplugin");
    }

    public static boolean openMessage(BaseFragment fragment, MessageObject message) {
        if (!isPluginFile(message.getFileName()) && !isPluginFile(message.getDocumentName())) {
            return false;
        }

        File file = null;

        if (message.messageOwner.attachPath != null && !message.messageOwner.attachPath.isEmpty()) {
            file = new File(message.messageOwner.attachPath);
        }

        if (file == null || !file.isFile()) {
            file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
        }

        openFile(fragment, file);

        return true;
    }

    public static void openFile(BaseFragment fragment, File file) {
        if (fragment == null) {
            return;
        }

        if (file == null || !file.isFile()) {
            new PluginInstallSheet(fragment).showError(new java.io.IOException(LocaleController.getString(R.string.FluxGramPluginDownloadFirst)));

            return;
        }

        open(fragment, Uri.fromFile(file));
    }

    public static void open(BaseFragment fragment, Uri uri) {
        PluginInstallSheet controller = new PluginInstallSheet(fragment);

        if (!controller.isUsable() || !reviewing.add(fragment)) {
            return;
        }

        ExternalPlugins.review(fragment.getParentActivity(), uri, (review, error) -> {
            reviewing.remove(fragment);

            if (!controller.isUsable() || !fragment.isLastFragment() || fragment.isPaused()) {
                if (review != null) {
                    review.close();
                }

                return;
            }

            if (error != null) {
                controller.showError(error);
            } else {
                controller.show(review);
            }
        });
    }

    private boolean isUsable() {
        return !fragment.isFinished && fragment.getParentActivity() != null && !fragment.getParentActivity().isFinishing();
    }

    private void showError(Exception error) {
        if (!isUsable()) {
            return;
        }

        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider()).setTitle(LocaleController.getString(R.string.FluxGramPluginError)).setMessage(error.getMessage()).setPositiveButton(LocaleController.getString(R.string.OK), null).create());
    }

    private TextView sheetText(Context context, CharSequence text, int size, boolean secondary) {
        TextView view = new LinkSpanDrawable.LinksTextView(context, fragment.getResourceProvider());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(fragment.getThemedColor(secondary ? Theme.key_dialogTextGray : Theme.key_dialogTextBlack));
        view.setPadding(0, dp(8), 0, dp(8));

        return view;
    }

    private void show(ExternalPlugins.Review review) {
        Context context = fragment.getParentActivity();
        PluginManifest manifest = review.result.manifest;
        Plugin existing = PluginManager.getInstance().getPlugin(manifest.id);
        boolean update = existing != null;
        PluginPackageStore.Pending pending = ExternalPlugins.getPending(manifest.id);
        boolean sameVersion = update && manifest.version.equals(existing.getVersion());
        boolean newer = !update || PluginPackageStore.compareVersions(manifest.version, existing.getVersion()) > 0;
        boolean canInstall = pending == null && newer;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(12));

        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int available = (int) (AndroidUtilities.displaySize.y * 0.85f) - dp(132);
                int limit = Math.min(MeasureSpec.getSize(heightMeasureSpec), available);

                if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                    limit = available;
                }

                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.max(dp(100), limit), MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(4), 0, dp(4), dp(12));

        FluxGramPluginIconView icon = new FluxGramPluginIconView(
                context, fragment.getResourceProvider(), manifest.id, manifest.name);
        icon.setRoundRadius(dp(20));

        if (review.icon != null) {
            icon.setImageBitmap(review.icon);
        }

        details.addView(icon, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 12));

        TextView title = sheetText(context, manifest.name, 21, false);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        details.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView metadata = sheetText(context,
                LocaleController.getString(R.string.FluxGramPluginVersion) + " " + manifest.version
                        + " · " + manifest.author, 14, true);
        metadata.setGravity(Gravity.CENTER);
        metadata.setPadding(0, 0, 0, dp(20));
        details.addView(metadata, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!canInstall) {
            details.addView(sheetText(context, LocaleController.getString(pending != null ? R.string.FluxGramPluginRestartRequired : sameVersion ? R.string.FluxGramPluginAlreadyInstalled : R.string.FluxGramPluginNewerVersionRequired), 15, true));
        }

        if (update && canInstall) {
            details.addView(sheetText(context, existing.getVersion() + " → " + manifest.version, 18, false));
            details.addView(sheetText(context, LocaleController.getString(R.string.FluxGramPluginUpdateInfo), 14, true));
        }

        details.addView(sheetText(context, manifest.description, 16, false));
        details.addView(sheetText(context, LocaleController.getString(R.string.FluxGramFullAccess), 17, false));
        details.addView(sheetText(context, LocaleController.getString(R.string.FluxGramPluginTrust), 15, false));

        scroll.addView(details);
        content.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView install = sheetText(context,
                LocaleController.getString(update ? R.string.FluxGramUpdatePlugin : R.string.FluxGramInstallPlugin),
                16, false);
        install.setEnabled(canInstall);
        install.setAlpha(canInstall ? 1f : 0.5f);
        install.setGravity(Gravity.CENTER);
        install.setTypeface(AndroidUtilities.bold());
        install.setTextColor(fragment.getThemedColor(Theme.key_featuredStickers_buttonText));
        install.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(25), fragment.getThemedColor(Theme.key_featuredStickers_addButton),
                fragment.getThemedColor(Theme.key_listSelector)));
        content.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 8, 0, 10));

        CheckBoxCell enable = new CheckBoxCell(
                context, CheckBoxCell.TYPE_CHECK_BOX_UNKNOWN, fragment.getResourceProvider());
        enable.setText(LocaleController.getString(update
                        ? R.string.FluxGramEnableAfterUpdate : R.string.FluxGramEnableAfterInstall),
                "", update && PluginManager.getInstance().isEnabled(manifest.id), false);
        enable.setEnabled(canInstall);
        enable.setAlpha(canInstall ? 1f : 0.5f);
        enable.setOnClickListener(view -> enable.setChecked(!enable.isChecked(), true));
        content.addView(enable, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, 50, Gravity.CENTER_HORIZONTAL));

        BottomSheet sheet = new BottomSheet.Builder(context, false, fragment.getResourceProvider()).setCustomView(content).create();
        boolean[] installing = {false};

        install.setOnClickListener(view -> {
            if (installing[0] || !canInstall) {
                return;
            }

            installing[0] = true;
            install.setEnabled(false);
            enable.setEnabled(false);
            install.setText(LocaleController.getString(R.string.FluxGramPluginInstalling));

            ExternalPlugins.install(review, enable.isChecked(), update ? existing.getVersion() : null, (id, error) -> {
                sheet.dismiss();

                if (!isUsable()) {
                    return;
                }

                if (error != null) {
                    showError(error);
                } else {
                    ExternalPlugins.changed();

                    if (update) {
                        showRestartInfo();
                    } else {
                        android.widget.Toast.makeText(context, LocaleController.getString(R.string.FluxGramPluginInstalled), android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

        if (fragment.showDialog(sheet, dialog -> {
            if (!installing[0]) {
                review.close();
            }
        }) == null) {
            review.close();
        }
    }

    private void showRestartInfo() {
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider()).setTitle(LocaleController.getString(R.string.FluxGramPluginRestartRequired)).setMessage(LocaleController.getString(R.string.FluxGramPluginRestartInfo)).setNegativeButton(LocaleController.getString(R.string.OK), null).setPositiveButton(LocaleController.getString(R.string.FluxGramOpenAndroidSettings), (dialog, which) -> fragment.getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + fragment.getParentActivity().getPackageName())))).create());
    }
}
