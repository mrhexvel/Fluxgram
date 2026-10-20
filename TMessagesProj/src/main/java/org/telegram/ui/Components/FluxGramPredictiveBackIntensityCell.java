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
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fluxgram.NavigationSettings;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramPredictiveBackIntensityCell extends FrameLayout {
    private static final int STEP = 5;

    private final Theme.ResourcesProvider resourcesProvider;
    private final TextView titleView;
    private final TextView valueView;
    private final TextView minimumView;
    private final TextView maximumView;
    private final SeekBarView seekBar;
    private int currentIntensity;
    private int hapticIntensity = -1;

    public FluxGramPredictiveBackIntensityCell(
            Context context,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        FrameLayout header = new FrameLayout(context);

        titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.FluxGramPredictiveBackIntensity));
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
        minimumView.setText(LocaleController.getString(R.string.FluxGramPredictiveBackLow));
        endpoints.addView(minimumView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL
        ));

        maximumView = createEndpointView(context, Gravity.END);
        maximumView.setText(LocaleController.getString(R.string.FluxGramPredictiveBackFull));
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

        int range = NavigationSettings.MAX_PREDICTIVE_BACK_INTENSITY
                - NavigationSettings.MIN_PREDICTIVE_BACK_INTENSITY;
        seekBar = new SeekBarView(context, true, resourcesProvider);
        seekBar.setReportChanges(true);
        seekBar.setSeparatorsCount(range / STEP + 1);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int steps = Math.round(progress * range / STEP);
                int intensity = NavigationSettings.MIN_PREDICTIVE_BACK_INTENSITY
                        + steps * STEP;
                setIntensity(intensity, true);

                if (stop) {
                    seekBar.setProgress(progressFor(intensity));
                    SharedConfig.setPredictiveBackIntensity(intensity);
                }
            }

            @Override
            public CharSequence getContentDescription() {
                return LocaleController.formatString(
                        R.string.FluxGramPredictiveBackIntensityValue,
                        currentIntensity
                );
            }

            @Override
            public int getStepsCount() {
                return range / STEP;
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
        setIntensity(SharedConfig.predictiveBackIntensity, false);
    }

    private TextView createEndpointView(Context context, int gravity) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setTypeface(AndroidUtilities.regular());
        view.setGravity(gravity);
        return view;
    }

    private void setIntensity(int intensity, boolean fromUser) {
        intensity = NavigationSettings.normalizePredictiveBackIntensity(intensity);
        if (intensity == currentIntensity && fromUser) {
            return;
        }

        currentIntensity = intensity;
        valueView.setText(LocaleController.formatString(
                R.string.FluxGramPredictiveBackIntensityValue,
                intensity
        ));

        if (!fromUser) {
            seekBar.setProgress(progressFor(intensity));
        } else if (hapticIntensity != intensity) {
            hapticIntensity = intensity;
            AndroidUtilities.vibrateCursor(seekBar);
        }
    }

    private float progressFor(int intensity) {
        int range = NavigationSettings.MAX_PREDICTIVE_BACK_INTENSITY
                - NavigationSettings.MIN_PREDICTIVE_BACK_INTENSITY;

        return (intensity - NavigationSettings.MIN_PREDICTIVE_BACK_INTENSITY) / (float) range;
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
