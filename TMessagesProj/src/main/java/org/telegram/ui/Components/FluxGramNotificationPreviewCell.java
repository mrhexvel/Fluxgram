package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramNotificationPreviewCell extends FrameLayout {
    public FluxGramNotificationPreviewCell(
            Context context,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);

        FrameLayout card = new FrameLayout(context);
        card.setClickable(false);
        card.setFocusable(false);
        card.setPadding(
                AndroidUtilities.dp(12),
                AndroidUtilities.dp(8),
                AndroidUtilities.dp(12),
                AndroidUtilities.dp(8)
        );

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(AndroidUtilities.dp(20));
        background.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        background.setStroke(
                AndroidUtilities.dp(1),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider)
        );
        card.setBackground(background);

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(24));
        imageView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        AvatarDrawable avatar = new AvatarDrawable();
        avatar.setInfo(1L, LocaleController.getString(R.string.AppName), null);
        imageView.setImageDrawable(avatar);
        card.addView(imageView, LayoutHelper.createFrameRelatively(
                48,
                48,
                Gravity.START | Gravity.CENTER_VERTICAL,
                12,
                12,
                12,
                12
        ));

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        card.addView(textColumn, LayoutHelper.createFrameRelatively(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL,
                72,
                12,
                16,
                12
        ));

        LinkSpanDrawable.LinksTextView titleTextView =
                new LinkSpanDrawable.LinksTextView(context);
        titleTextView.setPadding(0, 0, 0, AndroidUtilities.dp(3));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setSingleLine(true);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setTextColor(
                Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider)
        );
        titleTextView.setText(LocaleController.getString(R.string.AppName));
        textColumn.addView(titleTextView);

        LinkSpanDrawable.LinksTextView subtitleTextView =
                new LinkSpanDrawable.LinksTextView(context);
        subtitleTextView.setPadding(0, 0, 0, 0);
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleTextView.setTypeface(Typeface.SANS_SERIF);
        subtitleTextView.setSingleLine(false);
        subtitleTextView.setMaxLines(2);
        subtitleTextView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleTextView.setTextColor(
                Theme.getColor(Theme.key_dialogTextGray, resourcesProvider)
        );
        subtitleTextView.setText(
                LocaleController.getString(R.string.FluxGramNotificationPreviewText)
        );
        textColumn.addView(subtitleTextView);

        card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(card, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                96,
                Gravity.CENTER,
                12,
                8,
                12,
                8
        ));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(
                LocaleController.getString(R.string.AppName) + ". "
                        + LocaleController.getString(R.string.FluxGramNotificationPreviewText)
        );
    }

    public void previewVibration() {
        int feedback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY;
        performHapticFeedback(feedback);
    }
}
