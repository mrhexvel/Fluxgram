package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.StickerSettings;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramMessageAppearancePreviewCell extends FrameLayout {
    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable onSettingChanged;
    private final TextView valueView;
    private final TextView smallView;
    private final TextView largeView;
    private final SeekBarView seekBar;
    private final FluxGramStickerPreviewCell messagesPreview;
    private int stickerSize;

    public FluxGramMessageAppearancePreviewCell(
            Context context,
            int account,
            INavigationLayout navigationLayout,
            Theme.ResourcesProvider resourcesProvider,
            Runnable onSettingChanged
    ) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.onSettingChanged = onSettingChanged;

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        valueView.setTypeface(AndroidUtilities.bold());
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(dp(6), dp(1), dp(6), dp(1));
        valueView.setTextColor(color(Theme.key_windowBackgroundWhiteBlueHeader));
        valueView.setBackground(Theme.createRoundRectDrawable(
                dp(4),
                ColorUtils.setAlphaComponent(color(Theme.key_windowBackgroundWhiteBlueHeader), 38)
        ));
        addView(valueView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                19,
                Gravity.TOP | Gravity.START,
                21,
                17,
                0,
                0
        ));

        FrameLayout endpoints = new FrameLayout(context);
        smallView = createEndpointView(context, Gravity.START, R.string.FluxGramStickerSizeSmall);
        largeView = createEndpointView(context, Gravity.END, R.string.FluxGramStickerSizeLarge);
        endpoints.addView(smallView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL
        ));
        endpoints.addView(largeView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL
        ));
        addView(endpoints, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.FILL_HORIZONTAL,
                21,
                52,
                21,
                0
        ));

        seekBar = new SeekBarView(context, true, resourcesProvider);
        seekBar.setReportChanges(true);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int range = StickerSettings.MAX_STICKER_SIZE - StickerSettings.MIN_STICKER_SIZE;
                int size = StickerSettings.MIN_STICKER_SIZE + Math.round(progress * range);
                setStickerSize(size, false);

                if (stop) {
                    StickerSettings.setStickerSize(size);
                    seekBar.setProgress((size - StickerSettings.MIN_STICKER_SIZE) / (float) range);
                    onSettingChanged.run();
                }
            }

            @Override
            public CharSequence getContentDescription() {
                return LocaleController.formatString(R.string.FluxGramStickerSizeValue, stickerSize);
            }

            @Override
            public int getStepsCount() {
                return StickerSettings.MAX_STICKER_SIZE - StickerSettings.MIN_STICKER_SIZE;
            }
        });
        seekBar.setColors(color(Theme.key_switchTrack), color(Theme.key_switchTrackChecked));
        addView(seekBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                44,
                Gravity.TOP,
                6,
                68,
                6,
                0
        ));

        messagesPreview = new FluxGramStickerPreviewCell(
                context,
                account,
                navigationLayout,
                resourcesProvider
        );
        messagesPreview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(messagesPreview, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.FILL_HORIZONTAL,
                0,
                112,
                0,
                0
        ));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(LocaleController.getString(R.string.FluxGramMessageAppearancePreviewDescription));
        setStickerSize(StickerSettings.getStickerSize(), true);
    }

    public void updatePresentation() {
        setStickerSize(StickerSettings.getStickerSize(), true);
        messagesPreview.updatePresentation();
    }

    private TextView createEndpointView(Context context, int gravity, int textResource) {
        TextView view = new TextView(context);
        view.setText(LocaleController.getString(textResource));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setTypeface(AndroidUtilities.regular());
        view.setGravity(gravity);
        view.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText));
        return view;
    }

    private void setStickerSize(int size, boolean updateSeekBar) {
        size = Math.max(StickerSettings.MIN_STICKER_SIZE,
                Math.min(StickerSettings.MAX_STICKER_SIZE, size));
        stickerSize = size;
        valueView.setText(size == StickerSettings.MIN_STICKER_SIZE
                ? smallView.getText().toString().toUpperCase()
                : size == StickerSettings.MAX_STICKER_SIZE
                ? largeView.getText().toString().toUpperCase()
                : String.valueOf(size));

        if (updateSeekBar) {
            int range = StickerSettings.MAX_STICKER_SIZE - StickerSettings.MIN_STICKER_SIZE;
            seekBar.setProgress((size - StickerSettings.MIN_STICKER_SIZE) / (float) range);
        }
        messagesPreview.setStickerSize(size);
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
