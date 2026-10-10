package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.view.Gravity;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;

public class FluxGramPluginCard extends LinearLayout {
    public FluxGramPluginCard(
            Context context,
            Theme.ResourcesProvider provider,
            Plugin plugin,
            String name,
            String details,
            String description,
            boolean checked,
            boolean available,
            Runnable toggle,
            Runnable share,
            Runnable settings,
            Runnable delete) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));
        card.setBackground(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(20), Theme.getColor(Theme.key_windowBackgroundWhite, provider)));
        addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckCell title = new TextCheckCell(context, 18, false, provider);
        title.setTextAndValueAndCheck(name, details, checked, true, false);
        title.setEnabled(available);
        title.setAlpha(available ? 1f : 0.5f);
        title.setOnClickListener(view -> {
            if (available) {
                title.setChecked(!title.isChecked());
                toggle.run();
            }
        });
        if (plugin == null) {
            card.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));

            FluxGramPluginIconView icon = new FluxGramPluginIconView(context, provider, plugin.getId(), name);
            icon.load(plugin, provider);
            header.addView(icon, LayoutHelper.createLinear(56, 56));
            header.addView(title, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1));
            card.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (description != null && !description.isEmpty()) {
            LinkSpanDrawable.LinksTextView text = new LinkSpanDrawable.LinksTextView(context, provider);
            text.setText(description);
            text.setTextSize(15);
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider));
            text.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            card.addView(text, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 0, 18, 12));
        }

        if (share == null && settings == null && delete == null) {
            return;
        }

        View divider = new View(context);
        divider.setBackgroundColor(Theme.multAlpha(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, provider), 0.3f));
        card.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 18, 0, 18, 4));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        card.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addAction(actions, provider, R.drawable.msg_share,
                LocaleController.getString(R.string.FluxGramSharePlugin), share, false);
        addAction(actions, provider, R.drawable.msg_settings,
                LocaleController.getString(R.string.Settings), settings, false);

        if (delete != null) {
            View spacer = new View(context);
            spacer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

            addAction(actions, provider, R.drawable.msg_delete,
                    LocaleController.getString(R.string.Delete), delete, true);
        }
    }

    private void addAction(
            LinearLayout parent,
            Theme.ResourcesProvider provider,
            int icon,
            String label,
            Runnable action,
            boolean destructive) {
        if (action == null) {
            return;
        }

        RLottieImageView button = new RLottieImageView(getContext()) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setClassName("android.widget.Button");
            }
        };
        button.setImageResource(icon);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(destructive
                        ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayIcon, provider),
                PorterDuff.Mode.SRC_IN));
        button.setContentDescription(label);
        button.setFocusable(true);
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(12), 0, Theme.getColor(Theme.key_listSelector, provider)));
        button.setOnClickListener(view -> action.run());
        button.setOnLongClickListener(view -> {
            BulletinFactory.global().createSimpleBulletin(
                    getResources().getDrawable(icon).mutate(), label).show();
            return true;
        });

        parent.addView(button, LayoutHelper.createLinear(48, 48));
    }
}
