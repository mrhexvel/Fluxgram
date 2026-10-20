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
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fluxgram.AppearanceSettings;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramSectionRadiusCell extends FrameLayout {
    private final int account;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Utilities.Callback<Integer> onRadiusChanged;
    private final TextView titleView;
    private final TextView valueView;
    private final TextView minimumView;
    private final TextView maximumView;
    private final SeekBarView seekBar;
    private int currentRadius;
    private int hapticRadius = -1;

    public FluxGramSectionRadiusCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            Utilities.Callback<Integer> onRadiusChanged
    ) {
        super(context);
        this.account = account;
        this.resourcesProvider = resourcesProvider;
        this.onRadiusChanged = onRadiusChanged;

        FrameLayout header = new FrameLayout(context);

        titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.FluxGramSettingsAppearance));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.regular());
        header.addView(titleView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL
        ));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        valueView.setTypeface(AndroidUtilities.regular());
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(dp(6), dp(1), dp(6), dp(1));
        header.addView(valueView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                19,
                Gravity.END | Gravity.CENTER_VERTICAL
        ));
        addView(header, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                32,
                Gravity.TOP | Gravity.FILL_HORIZONTAL,
                21,
                10,
                21,
                0
        ));

        FrameLayout endpoints = new FrameLayout(context);
        minimumView = createEndpointView(context, Gravity.START);
        minimumView.setText("0");
        endpoints.addView(minimumView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL
        ));

        maximumView = createEndpointView(context, Gravity.END);
        maximumView.setText(String.valueOf(AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS));
        endpoints.addView(maximumView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL
        ));
        addView(endpoints, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                24,
                Gravity.TOP | Gravity.FILL_HORIZONTAL,
                21,
                42,
                21,
                0
        ));

        seekBar = new SeekBarView(context, true, resourcesProvider);
        seekBar.setReportChanges(true);
        seekBar.setSeparatorsCount(AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS + 1);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int radius = Math.round(progress * AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS);
                setRadius(radius, true);
                onRadiusChanged.run(radius);

                if (stop) {
                    seekBar.setProgress(radius / (float) AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS);
                    AppearanceSettings.setSettingsSectionRadius(account, radius);
                }
            }

            @Override
            public CharSequence getContentDescription() {
                return LocaleController.formatString(
                        R.string.FluxGramSectionCornerRadiusValue,
                        currentRadius
                );
            }

            @Override
            public int getStepsCount() {
                return AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS;
            }
        });
        addView(seekBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                44,
                Gravity.TOP,
                6,
                59,
                6,
                0
        ));

        update();
    }

    public void update() {
        int accent = color(Theme.key_windowBackgroundWhiteBlueHeader);
        int gray = color(Theme.key_windowBackgroundWhiteGrayText);

        titleView.setTextColor(accent);
        valueView.setTextColor(accent);
        valueView.setBackground(Theme.createRoundRectDrawable(
                dp(4),
                ColorUtils.setAlphaComponent(accent, 38)
        ));
        minimumView.setTextColor(gray);
        maximumView.setTextColor(gray);
        seekBar.setColors(color(Theme.key_switchTrack), color(Theme.key_switchTrackChecked));
        setRadius(AppearanceSettings.getSettingsSectionRadius(account), false);
    }

    private TextView createEndpointView(Context context, int gravity) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setTypeface(AndroidUtilities.regular());
        view.setGravity(gravity);
        return view;
    }

    private void setRadius(int radius, boolean fromUser) {
        radius = Math.max(0, Math.min(AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS, radius));
        if (radius == currentRadius && fromUser) {
            return;
        }

        currentRadius = radius;
        valueView.setText(LocaleController.formatString(
                R.string.FluxGramSectionCornerRadiusValue,
                radius
        ));

        if (!fromUser) {
            seekBar.setProgress(radius / (float) AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS);
        } else if (hapticRadius != radius) {
            hapticRadius = radius;
            AndroidUtilities.vibrateCursor(seekBar);
        }
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
