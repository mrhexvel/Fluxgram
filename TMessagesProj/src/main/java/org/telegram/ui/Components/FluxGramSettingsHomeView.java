package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;

public final class FluxGramSettingsHomeView extends LinearLayout {
    private final Theme.ResourcesProvider resourcesProvider;

    public FluxGramSettingsHomeView(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            boolean pluginsEnabled,
            Runnable openGeneral,
            Runnable openPlugins,
            Runnable openGhostMode,
            Runnable openStealth
    ) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);
        setPadding(dp(16), dp(12), dp(16), dp(32));
        addView(createIdentity(context), new LayoutParams(-1, dp(124)));

        SimpleTextView heading = text(
                context,
                LocaleController.getString(R.string.FluxGramYourSettings),
                14,
                Theme.key_windowBackgroundWhiteGrayText2
        );
        heading.setTypeface(AndroidUtilities.bold());

        LayoutParams headingParams = new LayoutParams(-1, dp(24));
        headingParams.setMargins(dp(8), dp(28), dp(8), dp(12));
        addView(heading, headingParams);

        addCategory(
                context,
                R.string.FluxGramGeneral,
                R.string.FluxGramGeneralDescription,
                R.drawable.msg_settings,
                openGeneral
        );

        addCategory(
                context,
                R.string.FluxGramGhostMode,
                R.string.FluxGramGhostModeDescription,
                R.drawable.settings_privacy,
                openGhostMode
        );

        addCategory(
                context,
                R.string.FluxGramStealth,
                R.string.FluxGramStealthDescription,
                R.drawable.msg_archive,
                openStealth
        );

        addCategory(
                context,
                R.string.FluxGramPlugins,
                pluginsEnabled ? R.string.FluxGramPluginsHomeEnabled : R.string.FluxGramPluginsHomeDisabled,
                R.drawable.msg_bots,
                openPlugins
        );
    }

    private View createIdentity(Context context) {
        FrameLayout header = new FrameLayout(context);

        boolean rtl = LocaleController.isRTL;
        int start = rtl ? Gravity.RIGHT : Gravity.LEFT;

        BackupImageView icon = new BackupImageView(context);
        icon.setRoundRadius(dp(18));
        icon.setImageDrawable(context.getApplicationInfo().loadIcon(context.getPackageManager()));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, LayoutHelper.createFrame(
                64, 64, start | Gravity.TOP, 24, 24, 24, 0
        ));

        SimpleTextView name = text(context, "FluxGram", 27, Theme.key_windowBackgroundWhiteBlackText);
        name.setTypeface(AndroidUtilities.bold());
        header.addView(name, LayoutHelper.createFrame(
                -1, 36, Gravity.TOP, rtl ? 24 : 104, 27, rtl ? 104 : 24, 0
        ));

        SimpleTextView version = text(context, version(context), 13, Theme.key_windowBackgroundWhiteGrayText2);
        header.addView(version, LayoutHelper.createFrame(
                -1, 22, Gravity.TOP, rtl ? 24 : 104, 63, rtl ? 104 : 24, 0
        ));

        return header;
    }

    private void addCategory(Context context, int title, int description, int icon, Runnable action) {
        TextDetailSettingsCell cell = new TextDetailSettingsCell(context);

        cell.setMultilineDetail(true);
        cell.setTextAndValueAndIcon(
                LocaleController.getString(title),
                LocaleController.getString(description),
                icon,
                false
        );
        cell.getTextView().setTypeface(AndroidUtilities.bold());
        cell.getTextView().setTextColor(color(Theme.key_windowBackgroundWhiteBlackText));
        cell.getValueTextView().setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2));
        cell.setPadding(0, dp(8), 0, dp(8));
        cell.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(20),
                color(Theme.key_windowBackgroundWhite),
                color(Theme.key_listSelector)
        ));
        cell.setOnClickListener(view -> action.run());

        LayoutParams params = new LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        addView(cell, params);
    }

    private SimpleTextView text(Context context, String value, int size, int colorKey) {
        SimpleTextView view = new SimpleTextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(colorKey));
        view.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

        return view;
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private String version(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            return info.versionName + " · " + info.versionCode;
        } catch (PackageManager.NameNotFoundException error) {
            return BuildVars.BUILD_VERSION_STRING;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int horizontalPadding = Math.max(dp(16), (width - dp(560)) / 2);
        setPadding(horizontalPadding, dp(12), horizontalPadding, dp(32));

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
