package org.telegram.ui.Components;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.FontSettings;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramFontPreviewCell extends LinearLayout {
    private final TextView regular;
    private final TextView bold;
    private final TextView italic;
    private final TextView monospace;

    public FluxGramFontPreviewCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        setOrientation(VERTICAL);
        setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));

        regular = createLine(context, resourcesProvider, 22);
        bold = createLine(context, resourcesProvider, 16);
        italic = createLine(context, resourcesProvider, 16);
        monospace = createLine(context, resourcesProvider, 15);

        regular.setText(LocaleController.getString(R.string.FluxGramFontPreviewTitle));
        bold.setText(LocaleController.getString(R.string.FluxGramFontPreviewBold));
        italic.setText(LocaleController.getString(R.string.FluxGramFontPreviewItalic));
        monospace.setText(LocaleController.getString(R.string.FluxGramFontPreviewMonospace));

        addView(regular, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34));
        addView(bold, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));
        addView(italic, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));
        addView(monospace, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));

        update();
    }

    public void update() {
        regular.setTypeface(FontSettings.getPreviewTypeface(FontSettings.Role.REGULAR));
        bold.setTypeface(FontSettings.getPreviewTypeface(FontSettings.Role.BOLD));
        italic.setTypeface(FontSettings.getPreviewTypeface(FontSettings.Role.ITALIC));
        monospace.setTypeface(FontSettings.getPreviewTypeface(FontSettings.Role.MONOSPACE));
    }

    private TextView createLine(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            int textSize) {
        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setSingleLine(true);

        return textView;
    }
}
