package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.text.InputFilter;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.ExternalPlugins;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.messenger.plugins.PluginSettings;
import org.telegram.messenger.plugins.PluginSettingsBlocks;
import org.telegram.messenger.plugins.PythonPluginAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

public final class PluginSettingsActivity extends BaseFragment {
    private final String pluginId;
    private UniversalRecyclerView listView;
    private volatile boolean destroyed;
    private volatile boolean blockViewActive;
    private boolean blockCleanupScheduled;
    private long blockCleanupDelay = 16;
    private final LinkedHashMap<PluginSettingsBlocks.Registration,
            PluginSettingsBlocks.Registration.Binding> blockBindings = new LinkedHashMap<>();

    private final Runnable refresh = () -> {
        if (!destroyed && listView != null) {
            listView.adapter.update(false);
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
        blockViewActive = false;
        closeBlockBindings();
        ExternalPlugins.removeListener(refresh);

        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        closeBlockBindings();
        blockViewActive = true;
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

        listView = new UniversalRecyclerView(this, (items, adapter) -> fillItems(items), null, null);
        listView.setItemAnimator(null);
        listView.adapter.setApplyBackground(false);
        FluxGramSettingsSections.apply(listView, currentAccount);
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        actionBar.setAdaptiveBackground(listView);

        return fragmentView;
    }

    @Override
    public void clearViews() {
        blockViewActive = false;
        closeBlockBindings();
        listView = null;
        super.clearViews();
    }

    private void fillItems(ArrayList<UItem> items) {
        Plugin plugin = PluginManager.getInstance().getPlugin(pluginId);

        if (!(plugin instanceof PythonPluginAdapter)) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramPluginSettingsUnavailable)));
            return;
        }

        PythonPluginAdapter adapter = (PythonPluginAdapter) plugin;
        PluginSettingsBlocks registry = adapter.getSettingsBlocks();
        List<PluginSettingsBlocks.Registration> registrations = registry == null
                ? java.util.Collections.emptyList() : registry.snapshot();
        reconcileBlockBindings(registrations);

        if (adapter.getSettingsSchema().isEmpty() && registrations.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramPluginSettingsEmpty)));
            return;
        }

        List<PluginSettings.Setting> schema = adapter.getSettingsSchema();
        ArrayList<PluginSettings.Setting> visibleSettings = new ArrayList<>();
        ArrayList<String> visibleLabels = new ArrayList<>();
        String pendingLabel = "";

        for (PluginSettings.Setting setting : schema) {
            if (!setting.label.isEmpty()) {
                pendingLabel = setting.label;
            }

            if (!setting.visible) {
                continue;
            }

            visibleSettings.add(setting);
            visibleLabels.add(pendingLabel);
            pendingLabel = "";
        }

        if (visibleSettings.isEmpty() && registrations.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramPluginSettingsEmpty)));
            return;
        }

        for (int index = 0; index < visibleSettings.size(); index++) {
            PluginSettings.Setting setting = visibleSettings.get(index);
            String labelText = visibleLabels.get(index);
            boolean divider = index + 1 < visibleSettings.size()
                    && visibleLabels.get(index + 1).isEmpty();

            if (!labelText.isEmpty()) {
                HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
                label.setText(labelText);
                items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
            }

            if (setting.type.equals("switch")) {
                TextCheckCell cell = new TextCheckCell(getParentActivity(), getResourceProvider());
                boolean checked = (Boolean) PluginSettings.getValue(pluginId, setting);

                if (setting.description.isEmpty()) {
                    cell.setTextAndCheck(setting.title, checked, divider);
                } else {
                    cell.setTextAndValueAndCheck(
                            setting.title, setting.description, checked, true, divider);
                }

                if (setting.iconResId != 0) {
                    cell.setIcon(setting.iconResId);
                }

                cell.setEnabled(canEdit(adapter, setting.key));
                cell.setOnClickListener(view -> {
                    if (!canEdit(adapter, setting.key)) {
                        return;
                    }

                    boolean value = !(Boolean) PluginSettings.getValue(pluginId, setting);

                    if (adapter.changeSettingValue(setting.key, value)) {
                        cell.setChecked(value);
                    }
                });
                items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
            } else {
                items.add(UItem.asCustom(
                        createValueCell(adapter, setting, divider), LayoutHelper.WRAP_CONTENT));
            }
        }

        for (PluginSettingsBlocks.Registration registration : registrations) {
            PluginSettingsBlocks.Registration.Binding binding = blockBindings.get(registration);

            if (binding == null || !binding.isActive()) {
                continue;
            }

            if (!items.isEmpty()) {
                items.add(UItem.asShadow(null));
            }

            if (!registration.getLabel().isEmpty()) {
                HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
                label.setText(registration.getLabel());
                items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
            }

            items.add(UItem.asCustom(binding.getView(), LayoutHelper.WRAP_CONTENT));
        }
    }

    private void reconcileBlockBindings(List<PluginSettingsBlocks.Registration> registrations) {
        HashSet<PluginSettingsBlocks.Registration> current = new HashSet<>(registrations);

        for (PluginSettingsBlocks.Registration registration
                : new ArrayList<>(blockBindings.keySet())) {
            if (!current.contains(registration)) {
                closeBlockBinding(registration);
            }
        }

        if (!blockViewActive) {
            return;
        }

        for (PluginSettingsBlocks.Registration registration : registrations) {
            if (blockBindings.containsKey(registration)) {
                continue;
            }

            try {
                blockBindings.put(
                        registration,
                        registration.bind(
                                getParentActivity(),
                                getResourceProvider(),
                                () -> blockViewActive && !destroyed));
            } catch (Exception | LinkageError | AssertionError error) {
                FileLog.e(error);
            }
        }
    }

    private void closeBlockBinding(PluginSettingsBlocks.Registration registration) {
        PluginSettingsBlocks.Registration.Binding binding = blockBindings.get(registration);

        if (binding == null) {
            return;
        }

        try {
            binding.close();
            blockBindings.remove(registration);
            blockCleanupDelay = 16;
        } catch (Exception | LinkageError | AssertionError error) {
            FileLog.e(error);
            scheduleBlockCleanup();
        }
    }

    private void closeBlockBindings() {
        for (PluginSettingsBlocks.Registration registration
                : new ArrayList<>(blockBindings.keySet())) {
            closeBlockBinding(registration);
        }
    }

    private void scheduleBlockCleanup() {
        if (blockCleanupScheduled) {
            return;
        }

        blockCleanupScheduled = true;
        long delay = blockCleanupDelay;
        blockCleanupDelay = Math.min(30_000, blockCleanupDelay * 2);
        AndroidUtilities.runOnUIThread(() -> {
            blockCleanupScheduled = false;
            closeBlockBindings();

            if (blockBindings.isEmpty() && blockViewActive && !destroyed && listView != null) {
                listView.adapter.update(false);
            }
        }, delay);
    }

    private boolean canEdit(PythonPluginAdapter adapter, String key) {
        return PluginManager.getInstance().getPlugin(pluginId) == adapter
                && !adapter.isStoppingForRemoval()
                && ExternalPlugins.getPending(pluginId) == null
                && adapter.isSettingEditable(key);
    }

    private View createValueCell(
            PythonPluginAdapter adapter, PluginSettings.Setting setting, boolean divider) {
        LinearLayout container = new LinearLayout(getParentActivity()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                if (divider) {
                    int offset = AndroidUtilities.dp(setting.iconResId == 0 ? 20 : 58);
                    canvas.drawLine(
                            LocaleController.isRTL ? 0 : offset,
                            getMeasuredHeight() - 1,
                            getMeasuredWidth() - (LocaleController.isRTL ? offset : 0),
                            getMeasuredHeight() - 1,
                            Theme.dividerPaint);
                }
            }
        };
        container.setOrientation(LinearLayout.VERTICAL);
        container.setAlpha(canEdit(adapter, setting.key) ? 1f : 0.5f);

        TextSettingsCell title = new TextSettingsCell(getParentActivity(), getResourceProvider());
        Object value = PluginSettings.getValue(pluginId, setting);
        title.setTextAndValue(setting.title, setting.displayValue(value), false);
        title.setIcon(setting.iconResId);
        container.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (setting.type.equals("select")) {
            title.setEnabled(canEdit(adapter, setting.key));
            title.setBackground(Theme.getSelectorDrawable(false));
            title.setOnClickListener(view -> {
                if (!canEdit(adapter, setting.key)) {
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
                            if (canEdit(adapter, setting.key)) {
                                String chosen = setting.optionValues.get(index);

                                if (adapter.changeSettingValue(setting.key, chosen)) {
                                    title.setTextAndValue(setting.title, setting.displayValue(chosen), false);
                                }
                            }
                        })
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .create());
            });
        } else if (setting.type.equals("text")) {
            title.setEnabled(canEdit(adapter, setting.key));
            title.setBackground(Theme.getSelectorDrawable(false));
            title.setOnClickListener(view -> {
                if (canEdit(adapter, setting.key)) {
                    showTextEditor(adapter, setting, title);
                }
            });
        } else {
            SeekBarView slider = new SeekBarView(getParentActivity(), false, getResourceProvider());
            slider.setReportChanges(true);
            int steps = (setting.maximum - setting.minimum) / setting.step;
            int[] current = {(Integer) value};
            slider.setProgress((current[0] - setting.minimum) / (float) (setting.maximum - setting.minimum));
            slider.setEnabled(canEdit(adapter, setting.key));
            slider.setContentDescription(setting.title + ": " + setting.displayValue(value));
            slider.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    current[0] = setting.minimum + Math.max(0, Math.min(steps, Math.round(progress * steps))) * setting.step;
                    title.setTextAndValue(setting.title, setting.displayValue(current[0]), false);
                    slider.setContentDescription(setting.title + ": " + setting.displayValue(current[0]));

                    if (stop && canEdit(adapter, setting.key)
                            && adapter.changeSettingValue(setting.key, current[0])) {
                        slider.setProgress((current[0] - setting.minimum)
                                / (float) (setting.maximum - setting.minimum));
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
            int horizontalMargin = setting.iconResId == 0 ? 21 : 58;
            container.addView(description, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    horizontalMargin, 0, horizontalMargin, 12));
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
            if (!canEdit(adapter, setting.key)) {
                dialog.dismiss();
                return;
            }

            String value = input.getText().toString();

            if (!setting.accepts(value)) {
                AndroidUtilities.shakeView(input);
                return;
            }

            if (!value.equals(PluginSettings.getValue(pluginId, setting))) {
                if (!adapter.changeSettingValue(setting.key, value)) {
                    dialog.dismiss();
                    return;
                }
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

        FluxGramSettingsSections.apply(listView, currentAccount);
        refresh.run();
    }
}
