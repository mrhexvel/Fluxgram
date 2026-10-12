package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.ExternalPlugins;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.messenger.plugins.PluginSettings;
import org.telegram.messenger.plugins.PythonPluginAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.PluginComposerPreviewView;

import java.util.ArrayList;

public final class PluginSettingsActivity extends BaseFragment {
    private final String pluginId;
    private UniversalRecyclerView listView;
    private PluginComposerPreviewView preview;
    private PythonPluginAdapter previewAdapter;
    private boolean destroyed;

    private final Runnable refresh = () -> {
        if (!destroyed && listView != null) {
            listView.adapter.update(false);

            if (preview != null) {
                preview.refresh();
            }
        }
    };

    public PluginSettingsActivity(String pluginId) {
        this.pluginId = pluginId;
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

        if (preview != null) {
            preview.destroy();
            preview = null;
            previewAdapter = null;
        }

        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        if (preview != null) {
            preview.destroy();
            preview = null;
            previewAdapter = null;
        }

        Plugin plugin = PluginManager.getInstance().getPlugin(pluginId);
        actionBar.setTitle(plugin == null ? LocaleController.getString(R.string.Settings) : plugin.getName());
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

        if (plugin instanceof PythonPluginAdapter
                && ((PythonPluginAdapter) plugin).hasJavaModule()) {
            previewAdapter = (PythonPluginAdapter) plugin;
            preview = new PluginComposerPreviewView(
                    context, currentAccount, pluginId,
                    () -> canPreview(previewAdapter), getResourceProvider());
        }

        listView = new UniversalRecyclerView(this, (items, adapter) -> fillItems(items), null, null);
        listView.setItemAnimator(null);
        listView.adapter.setApplyBackground(false);
        listView.setSections(AndroidUtilities.dp(12), AndroidUtilities.dp(20), false);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar.setAdaptiveBackground(listView);

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items) {
        Plugin plugin = PluginManager.getInstance().getPlugin(pluginId);

        if (!(plugin instanceof PythonPluginAdapter)) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramPluginSettingsUnavailable)));
            return;
        }

        PythonPluginAdapter adapter = (PythonPluginAdapter) plugin;

        if (preview != null && adapter == previewAdapter) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.FluxGramPluginPreview)));
            items.add(UItem.asCustomShadow(preview, 112));
        }

        if (adapter.getSettingsSchema().isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramPluginSettingsEmpty)));
            return;
        }

        for (PluginSettings.Setting setting : adapter.getSettingsSchema()) {
            if (!setting.label.isEmpty()) {
                HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
                label.setText(setting.label);
                items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
            }

            if (setting.type.equals("switch")) {
                TextCheckCell cell = new TextCheckCell(getParentActivity(), getResourceProvider());
                cell.setTextAndValueAndCheck(setting.title, setting.description,
                        (Boolean) PluginSettings.getValue(pluginId, setting), true, false);
                cell.setEnabled(canEdit(adapter));
                cell.setOnClickListener(view -> {
                    if (!canEdit(adapter)) {
                        return;
                    }

                    boolean value = !(Boolean) PluginSettings.getValue(pluginId, setting);
                    adapter.changeSettingValue(setting.key, value);
                    cell.setChecked(value);
                });
                items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
            } else {
                items.add(UItem.asCustom(createValueCell(adapter, setting), LayoutHelper.WRAP_CONTENT));
            }
        }
    }

    private boolean canEdit(PythonPluginAdapter adapter) {
        return PluginManager.getInstance().getPlugin(pluginId) == adapter
                && !adapter.isStoppingForRemoval() && ExternalPlugins.getPending(pluginId) == null;
    }

    private boolean canPreview(PythonPluginAdapter adapter) {
        PluginManager manager = PluginManager.getInstance();
        return canEdit(adapter) && manager.isSystemEnabled() && manager.isEnabled(pluginId);
    }

    private View createValueCell(PythonPluginAdapter adapter, PluginSettings.Setting setting) {
        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setAlpha(canEdit(adapter) ? 1f : 0.5f);

        TextSettingsCell title = new TextSettingsCell(getParentActivity(), getResourceProvider());
        Object value = PluginSettings.getValue(pluginId, setting);
        title.setTextAndValue(setting.title, setting.displayValue(value), false);
        container.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (setting.type.equals("select")) {
            title.setEnabled(canEdit(adapter));
            title.setBackground(Theme.getSelectorDrawable(false));
            title.setOnClickListener(view -> {
                if (!canEdit(adapter)) {
                    return;
                }

                CharSequence[] options = new CharSequence[setting.optionTitles.size()];
                Object selected = PluginSettings.getValue(pluginId, setting);

                for (int i = 0; i < options.length; i++) {
                    options[i] = (setting.optionValues.get(i).equals(selected) ? "✓  " : "")
                            + setting.optionTitles.get(i);
                }

                showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(setting.title)
                        .setItems(options, (dialog, index) -> {
                            if (canEdit(adapter)) {
                                String chosen = setting.optionValues.get(index);
                                adapter.changeSettingValue(setting.key, chosen);
                                title.setTextAndValue(setting.title, setting.displayValue(chosen), false);
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .create());
            });
        } else if (setting.type.equals("text")) {
            title.setEnabled(canEdit(adapter));
            title.setBackground(Theme.getSelectorDrawable(false));
            title.setOnClickListener(view -> {
                if (canEdit(adapter)) {
                    showTextEditor(adapter, setting, title);
                }
            });
        } else {
            SeekBarView slider = new SeekBarView(getParentActivity(), false, getResourceProvider());
            slider.setReportChanges(true);
            int steps = (setting.maximum - setting.minimum) / setting.step;
            int[] current = {(Integer) value};
            slider.setProgress((current[0] - setting.minimum) / (float) (setting.maximum - setting.minimum));
            slider.setEnabled(canEdit(adapter));
            slider.setContentDescription(setting.title + ": " + setting.displayValue(value));
            slider.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    current[0] = setting.minimum + Math.max(0, Math.min(steps, Math.round(progress * steps))) * setting.step;
                    title.setTextAndValue(setting.title, setting.displayValue(current[0]), false);
                    slider.setContentDescription(setting.title + ": " + setting.displayValue(current[0]));

                    if (stop && canEdit(adapter)) {
                        adapter.changeSettingValue(setting.key, current[0]);
                        slider.setProgress((current[0] - setting.minimum) / (float) (setting.maximum - setting.minimum));
                    }
                }

                @Override
                public int getStepsCount() {
                    return steps;
                }

                @Override
                public CharSequence getContentDescription() {
                    return setting.title + ": " + setting.displayValue(current[0]);
                }
            });
            container.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 12, 0, 12, 0));
        }

        if (!setting.description.isEmpty()) {
            LinkSpanDrawable.LinksTextView description = new LinkSpanDrawable.LinksTextView(
                    getParentActivity(), getResourceProvider());
            description.setText(setting.description);
            description.setTextSize(14);
            description.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            container.addView(description, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 21, 0, 21, 12));
        }

        return container;
    }

    private void showTextEditor(PythonPluginAdapter adapter, PluginSettings.Setting setting, TextSettingsCell cell) {
        EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
        input.setTextSize(16);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextGray));
        input.setCursorColor(getThemedColor(Theme.key_dialogTextLink));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint(setting.placeholder);
        input.setContentDescription(setting.title);
        input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        input.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.multAlpha(getThemedColor(Theme.key_dialogTextBlack), 0.06f)));
        input.setFilters(new InputFilter[] {(source, start, end, destination, dstart, dend) -> {
            int remaining = setting.maxLength
                    - Character.codePointCount(destination, 0, dstart)
                    - Character.codePointCount(destination, dend, destination.length());
            int count = Character.codePointCount(source, start, end);

            if (count <= remaining) {
                return null;
            }

            if (remaining <= 0) {
                return "";
            }

            int cut = Character.offsetByCodePoints(source, start, remaining);
            return source.subSequence(start, cut);
        }});
        input.setText((String) PluginSettings.getValue(pluginId, setting));
        input.setSelection(input.length());

        FrameLayout container = new FrameLayout(getParentActivity());
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                android.view.Gravity.TOP, 20, 8, 20, 8));

        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(setting.title)
                .setView(container)
                .setPositiveButton(LocaleController.getString(R.string.Save), null)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();

        Runnable save = () -> {
            if (!canEdit(adapter)) {
                dialog.dismiss();
                return;
            }

            String value = input.getText().toString();

            if (!setting.accepts(value)) {
                AndroidUtilities.shakeView(input);
                return;
            }

            if (!value.equals(PluginSettings.getValue(pluginId, setting))) {
                adapter.changeSettingValue(setting.key, value);
            }

            cell.setTextAndValue(setting.title, setting.displayValue(value), false);
            dialog.dismiss();
        };
        input.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                save.run();
                return true;
            }

            return false;
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> save.run());
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        showDialog(dialog, ignored -> AndroidUtilities.hideKeyboard(input));
    }

    @Override
    public void onResume() {
        super.onResume();

        if (preview != null) {
            preview.setActive(true);
        }

        refresh.run();
    }

    @Override
    public void onPause() {
        if (preview != null) {
            preview.setActive(false);
        }

        super.onPause();
    }
}
