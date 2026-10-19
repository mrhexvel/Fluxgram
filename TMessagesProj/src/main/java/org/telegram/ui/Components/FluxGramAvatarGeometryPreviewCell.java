package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.AppearanceSettings;
import org.telegram.messenger.fluxgram.AvatarGeometry;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public final class FluxGramAvatarGeometryPreviewCell extends FrameLayout {
    private final int account;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Runnable onSettingChanged;
    private final TextView titleView;
    private final TextView valueView;
    private final TextView squareView;
    private final TextView circleView;
    private final SeekBarView seekBar;
    private final PreviewView previewView;
    private int currentRadius;
    private int hapticRadius = -1;
    private boolean unifiedRadius;

    public FluxGramAvatarGeometryPreviewCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider,
            Runnable onSettingChanged
    ) {
        super(context);
        this.account = account;
        this.resourcesProvider = resourcesProvider;
        this.onSettingChanged = onSettingChanged;

        setWillNotDraw(false);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.FluxGramAvatarCornerRadius));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        header.addView(titleView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
        ));

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        valueView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(dp(6), dp(1), dp(6), dp(1));
        header.addView(valueView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                19,
                Gravity.CENTER_VERTICAL,
                6,
                0,
                0,
                0
        ));

        addView(header, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.FILL_HORIZONTAL,
                21,
                16,
                21,
                0
        ));

        FrameLayout endpoints = new FrameLayout(context);
        squareView = createEndpointView(context, Gravity.START);
        squareView.setText(LocaleController.getString(R.string.FluxGramAvatarSquare));
        endpoints.addView(squareView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL
        ));

        circleView = createEndpointView(context, Gravity.END);
        circleView.setText(LocaleController.getString(R.string.FluxGramAvatarCircle));
        endpoints.addView(circleView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL
        ));
        addView(endpoints, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.FILL_HORIZONTAL,
                21,
                48,
                21,
                0
        ));

        seekBar = new SeekBarView(context, true, resourcesProvider);
        seekBar.setReportChanges(true);
        seekBar.setSeparatorsCount(AvatarGeometry.MAX_CORNER_RADIUS + 1);
        seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                int radius = Math.round(progress * AvatarGeometry.MAX_CORNER_RADIUS);
                setRadius(radius, true);

                if (stop) {
                    seekBar.setProgress(radius / (float) AvatarGeometry.MAX_CORNER_RADIUS);
                    AppearanceSettings.setAvatarCornerRadius(account, radius);
                    onSettingChanged.run();
                }
            }

            @Override
            public CharSequence getContentDescription() {
                return LocaleController.formatString(
                        R.string.FluxGramAvatarCornerRadiusValue,
                        currentRadius
                );
            }

            @Override
            public int getStepsCount() {
                return AvatarGeometry.MAX_CORNER_RADIUS;
            }
        });
        addView(seekBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                44,
                Gravity.TOP,
                6,
                65,
                6,
                0
        ));

        previewView = new PreviewView(context);
        addView(previewView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                89,
                Gravity.TOP | Gravity.FILL_HORIZONTAL,
                21,
                112,
                21,
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
        squareView.setTextColor(gray);
        circleView.setTextColor(gray);
        seekBar.setColors(color(Theme.key_switchTrack), color(Theme.key_switchTrackChecked));

        unifiedRadius = AppearanceSettings.usesUnifiedAvatarRadius(account);
        setRadius(AppearanceSettings.getAvatarCornerRadius(account), false);
        previewView.invalidate();
    }

    private TextView createEndpointView(Context context, int gravity) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setGravity(gravity);
        return view;
    }

    private void setRadius(int radius, boolean fromUser) {
        radius = Math.max(0, Math.min(AvatarGeometry.MAX_CORNER_RADIUS, radius));
        if (radius == currentRadius && fromUser) {
            return;
        }

        currentRadius = radius;
        if (!fromUser) {
            seekBar.setProgress(radius / (float) AvatarGeometry.MAX_CORNER_RADIUS);
        }
        valueView.setText(valueText(radius));
        updateEndpointColors();

        if (fromUser && (radius == 0 || radius == AvatarGeometry.MAX_CORNER_RADIUS)) {
            if (hapticRadius != radius) {
                hapticRadius = radius;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
        } else if (radius > 0 && radius < AvatarGeometry.MAX_CORNER_RADIUS) {
            hapticRadius = -1;
        }

        previewView.invalidate();
    }

    private CharSequence valueText(int radius) {
        if (radius == 0) {
            return LocaleController.getString(R.string.FluxGramAvatarSquare)
                    .toUpperCase(Locale.getDefault());
        }
        if (radius == AvatarGeometry.MAX_CORNER_RADIUS) {
            return LocaleController.getString(R.string.FluxGramAvatarCircle)
                    .toUpperCase(Locale.getDefault());
        }

        return String.valueOf(radius);
    }

    private void updateEndpointColors() {
        int gray = color(Theme.key_windowBackgroundWhiteGrayText);
        int accent = color(Theme.key_windowBackgroundWhiteBlueText);
        float half = AvatarGeometry.MAX_CORNER_RADIUS / 2f;

        if (currentRadius <= half / 2f) {
            float progress = 1f - currentRadius / (half / 2f);
            squareView.setTextColor(ColorUtils.blendARGB(gray, accent, progress));
            circleView.setTextColor(gray);
        } else if (currentRadius >= half * 1.5f) {
            float progress = (currentRadius - half * 1.5f)
                    / (AvatarGeometry.MAX_CORNER_RADIUS - half * 1.5f);
            squareView.setTextColor(gray);
            circleView.setTextColor(ColorUtils.blendARGB(gray, accent, progress));
        } else {
            squareView.setTextColor(gray);
            circleView.setTextColor(gray);
        }
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private final class PreviewView extends FrameLayout {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint primaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint secondaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private PreviewView(Context context) {
            super(context);
            setWillNotDraw(false);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(0.5f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int textColor = color(Theme.key_windowBackgroundWhiteBlackText);
            int grayColor = color(Theme.key_windowBackgroundWhiteGrayText2);
            int backgroundColor = ColorUtils.blendARGB(
                    color(Theme.key_windowBackgroundWhite),
                    textColor,
                    Theme.isCurrentThemeDark() ? 0.05f : 0.035f
            );

            backgroundPaint.setColor(backgroundColor);
            strokePaint.setColor(ColorUtils.blendARGB(
                    color(Theme.key_windowBackgroundWhite),
                    textColor,
                    Theme.isCurrentThemeDark() ? 0.135f : 0.12f
            ));
            primaryPaint.setColor(ColorUtils.setAlphaComponent(grayColor, 102));
            secondaryPaint.setColor(ColorUtils.setAlphaComponent(grayColor, 51));

            canvas.save();
            if (LocaleController.isRTL) {
                canvas.scale(-1, 1, getWidth() / 2f, 0);
            }

            rect.set(dp(0.5f), dp(0.5f), getWidth() - dp(0.5f), getHeight() - dp(0.5f));
            canvas.drawRoundRect(rect, dp(12), dp(12), backgroundPaint);
            canvas.drawRoundRect(rect, dp(12), dp(12), strokePaint);

            float avatarLeft = dp(15);
            float avatarTop = dp(14);
            int avatarSize = dp(56);
            int radius = AvatarGeometry.resolveRadius(
                    currentRadius,
                    unifiedRadius,
                    avatarSize,
                    AvatarGeometry.ROLE_USER,
                    dp(28)
            );
            rect.set(
                    avatarLeft,
                    avatarTop,
                    avatarLeft + avatarSize,
                    avatarTop + avatarSize
            );
            canvas.drawRoundRect(rect, radius, radius, primaryPaint);

            float squareness = 1f - currentRadius / (float) AvatarGeometry.MAX_CORNER_RADIUS;
            float outerRadius = dp(7 + 2 * squareness);
            float innerRadius = dp(5 + squareness);
            float offset = dp(8) + (float) (outerRadius / Math.sqrt(2) - dp(8)) * squareness;
            float dotX = avatarLeft + avatarSize - offset;
            float dotY = avatarTop + avatarSize - offset;

            backgroundPaint.setColor(backgroundColor);
            canvas.drawCircle(dotX, dotY, outerRadius, backgroundPaint);
            backgroundPaint.setColor(color(Theme.key_chats_onlineCircle));
            canvas.drawCircle(dotX, dotY, innerRadius, backgroundPaint);

            float textLeft = dp(83);
            drawMockLine(canvas, textLeft, dp(17), getWidth() * 0.4f, primaryPaint);
            drawMockLine(canvas, textLeft, dp(39), getWidth() * 0.5f, secondaryPaint);
            drawMockLine(canvas, textLeft, dp(57), getWidth() * 0.36f, secondaryPaint);
            drawMockLine(
                    canvas,
                    getWidth() - dp(59),
                    dp(17),
                    dp(43),
                    secondaryPaint
            );
            canvas.restore();
        }

        private void drawMockLine(
                Canvas canvas,
                float left,
                float top,
                float width,
                Paint paint
        ) {
            rect.set(left, top, Math.min(getWidth() - dp(16), left + width), top + dp(8));
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
        }
    }
}
