package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.ExternalPlugins;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public final class FluxGramPluginRecoverySheet extends BottomSheet {
    private static boolean showing;

    private final BaseFragment fragment;

    private FluxGramPluginRecoverySheet(BaseFragment fragment, String pluginLabel) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        this.fragment = fragment;

        Context context = fragment.getParentActivity();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(12));

        TextView title = new TextView(context);
        title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(R.string.FluxGramPluginRecoveryTitle));
        content.addView(title, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView message = new TextView(context);
        message.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        message.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        message.setLineSpacing(dp(2), 1f);
        message.setText(LocaleController.formatString(
                "FluxGramPluginRecoveryMessage",
                R.string.FluxGramPluginRecoveryMessage,
                pluginLabel));
        content.addView(message, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 20));

        ButtonWithCounterView review = new ButtonWithCounterView(
                context, resourcesProvider).setRound();
        review.setText(LocaleController.getString(R.string.FluxGramPluginRecoveryReview), false);
        review.setOnClickListener(view -> openPlugins());
        content.addView(review, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 48, 0, 0, 0, 0));

        ButtonWithCounterView keepDisabled = new ButtonWithCounterView(
                context, resourcesProvider).setRound().setNeutral();
        keepDisabled.setText(
                LocaleController.getString(R.string.FluxGramPluginRecoveryKeepDisabled), false);
        keepDisabled.setOnClickListener(view -> dismiss());
        content.addView(keepDisabled, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 48, 0, 6, 0, 0));

        setCustomView(content);
        setOnDismissListener(dialog -> showing = false);
    }

    public static void maybeShow() {
        PluginManager manager = PluginManager.getInstance();

        if (showing || !manager.hasRecoveryNotice()) {
            return;
        }

        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        Activity activity = fragment == null ? null : fragment.getParentActivity();

        if (activity == null || activity.isFinishing()) {
            return;
        }

        String pluginId = manager.getRecoveryPluginId();
        Plugin plugin = pluginId == null ? null : manager.getPlugin(pluginId);
        String pluginLabel = plugin == null ? pluginId : plugin.getName() + " (" + pluginId + ")";

        if (pluginLabel == null) {
            pluginLabel = LocaleController.getString(R.string.FluxGramPluginRecoveryUnknown);
        }

        FluxGramPluginRecoverySheet sheet = new FluxGramPluginRecoverySheet(
                fragment, pluginLabel);
        showing = true;

        try {
            sheet.show();

            if (!manager.claimRecoveryNotice()) {
                sheet.dismiss();
            }
        } catch (RuntimeException error) {
            showing = false;
            FileLog.e(error);
        }
    }

    private void openPlugins() {
        PluginManager manager = PluginManager.getInstance();
        manager.setSystemEnabled(true);

        if (manager.isRecoveryLatched()) {
            return;
        }

        ExternalPlugins.changed();
        dismiss();
        fragment.presentFragment(FluxGramSettingsActivity.plugins());
    }
}
