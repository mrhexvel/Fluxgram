package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramPluginCell extends LinearLayout {
    public FluxGramPluginCell(
            Context context,
            Theme.ResourcesProvider provider,
            Plugin plugin,
            String name,
            String details,
            String description,
            boolean checked,
            boolean available,
            Runnable toggle,
            View.OnClickListener openActions) {
        super(context);
        setOrientation(VERTICAL);
        setPaddingRelative(
                AndroidUtilities.dp(16),
                AndroidUtilities.dp(8),
                AndroidUtilities.dp(8),
                AndroidUtilities.dp(10));

        Switch switchView = new Switch(context);
        switchView.setColors(
                Theme.key_switchTrack,
                Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhite);
        switchView.setChecked(checked, false);
        switchView.setEnabled(available);
        switchView.setFocusable(false);
        switchView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout row = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setClassName("android.widget.Switch");
                info.setCheckable(available);
                info.setChecked(switchView.isChecked());
            }
        };
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setContentDescription(name + ", " + details
                + (description == null || description.isEmpty() ? "" : ". " + description));
        row.setFocusable(true);
        row.setBackground(Theme.getSelectorDrawable(false));

        FluxGramPluginIconView icon = new FluxGramPluginIconView(
                context, provider, plugin.getId(), name);
        icon.load(plugin, provider);
        icon.setAlpha(available ? 1f : 0.5f);
        row.addView(icon, LayoutHelper.createLinear(48, 48));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, LayoutHelper.WRAP_CONTENT, 1);
        contentParams.setMarginStart(AndroidUtilities.dp(12));
        row.addView(content, contentParams);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setAlpha(available ? 1f : 0.5f);

        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(16);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider));
        title.setTypeface(AndroidUtilities.bold());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        labels.addView(title, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText(details);
        subtitle.setTextSize(13);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, provider));
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        subtitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        labels.addView(subtitle, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, LayoutHelper.WRAP_CONTENT, 1);
        labelsParams.setMarginEnd(AndroidUtilities.dp(8));
        header.addView(labels, labelsParams);
        header.addView(switchView, LayoutHelper.createLinear(37, 40));
        header.addView(createActionsButton(context, provider, openActions),
                LayoutHelper.createLinear(40, 40));
        content.addView(header, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View.OnClickListener toggleListener = view -> {
            if (!available || toggle == null) {
                return;
            }

            switchView.setChecked(!switchView.isChecked(), true);
            toggle.run();
        };
        row.setOnClickListener(toggleListener);
        switchView.setOnClickListener(toggleListener);

        if (description != null && !description.isEmpty()) {
            LinkSpanDrawable.LinksTextView text = new LinkSpanDrawable.LinksTextView(context, provider);
            text.setText(description);
            text.setTextSize(14);
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, provider));
            text.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, provider));
            text.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            textParams.setMarginEnd(AndroidUtilities.dp(8));
            textParams.topMargin = AndroidUtilities.dp(3);
            content.addView(text, textParams);
        }

        addView(row, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private View createActionsButton(
            Context context,
            Theme.ResourcesProvider provider,
            View.OnClickListener openActions) {
        RLottieImageView button = new RLottieImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setClassName("android.widget.Button");
            }
        };
        button.setImageResource(R.drawable.ic_ab_other);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, provider),
                PorterDuff.Mode.SRC_IN));
        button.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        button.setFocusable(true);
        button.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_dialogButtonSelector, provider),
                1,
                AndroidUtilities.dp(20)
        ));
        button.setOnClickListener(openActions);

        return button;
    }
}
