package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramSettingsHeaderCell extends FrameLayout {
    public FluxGramSettingsHeaderCell(
            Context context,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);

        BackupImageView icon = new BackupImageView(context);
        icon.setRoundRadius(dp(24));
        icon.setImageDrawable(context.getApplicationInfo().loadIcon(context.getPackageManager()));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, LayoutHelper.createFrame(
                96, 96, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0
        ));

        SimpleTextView name = text(
                context,
                "FluxGram",
                25,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)
        );
        name.setTypeface(AndroidUtilities.bold());
        addView(name, LayoutHelper.createFrame(
                -1, 38, Gravity.TOP, 24, 125, 24, 0
        ));

        SimpleTextView version = text(
                context,
                version(context),
                13,
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider)
        );
        addView(version, LayoutHelper.createFrame(
                -1, 24, Gravity.TOP, 24, 165, 24, 0
        ));
    }

    private SimpleTextView text(Context context, String value, int size, int color) {
        SimpleTextView view = new SimpleTextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);

        return view;
    }

    private String version(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            return info.versionName + " (" + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException error) {
            return BuildVars.BUILD_VERSION_STRING;
        }
    }
}
