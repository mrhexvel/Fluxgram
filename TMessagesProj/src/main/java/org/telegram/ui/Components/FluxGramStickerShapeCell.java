package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.fluxgram.StickerSettings;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramStickerShapeCell extends LinearLayout {
    private final ShapeOption[] options = new ShapeOption[3];
    private final Runnable onSettingChanged;

    public FluxGramStickerShapeCell(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            Runnable onSettingChanged
    ) {
        super(context);
        this.onSettingChanged = onSettingChanged;

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(13), dp(10), dp(13), 0);

        for (int shape = StickerSettings.SHAPE_DEFAULT;
             shape <= StickerSettings.SHAPE_MESSAGE;
             shape++) {
            ShapeOption option = new ShapeOption(context, resourcesProvider, shape);
            options[shape] = option;
            addView(option, LayoutHelper.createLinear(
                    0,
                    LayoutHelper.MATCH_PARENT,
                    1.0f,
                    8,
                    0,
                    8,
                    0
            ));
            int selectedShape = shape;
            option.setOnClickListener(view -> selectShape(selectedShape));
        }

        update(false);
    }

    public void update(boolean animated) {
        int selected = StickerSettings.getStickerShape();
        for (int shape = StickerSettings.SHAPE_DEFAULT;
             shape <= StickerSettings.SHAPE_MESSAGE;
             shape++) {
            options[shape].setSelectedState(shape == selected, animated);
        }
    }

    private void selectShape(int shape) {
        if (shape == StickerSettings.getStickerShape()) {
            return;
        }

        StickerSettings.setStickerShape(shape);
        update(true);
        onSettingChanged.run();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(130), MeasureSpec.EXACTLY)
        );
    }

    private static final class ShapeOption extends View {
        private final Theme.ResourcesProvider resourcesProvider;
        private final int shape;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF stickerRect = new RectF();
        private final Path stickerPath = new Path();
        private ValueAnimator animator;
        private float selectionProgress;

        private ShapeOption(Context context, Theme.ResourcesProvider resourcesProvider, int shape) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            this.shape = shape;

            setClickable(true);
            setFocusable(true);
            setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector, resourcesProvider),
                    7
            ));
        }

        private void setSelectedState(boolean selected, boolean animated) {
            setSelected(selected);
            float target = selected ? 1.0f : 0.0f;
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (!animated) {
                selectionProgress = target;
                invalidate();
                return;
            }

            animator = ValueAnimator.ofFloat(selectionProgress, target);
            animator.setDuration(250);
            animator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            animator.addUpdateListener(valueAnimator -> {
                selectionProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider);
            int card = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
            int text = ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider),
                    accent,
                    selectionProgress
            );

            cardRect.set(0, 0, width, dp(80));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(card);
            canvas.drawRoundRect(cardRect, dp(10), dp(10), paint);
            if (selectionProgress > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(ColorUtils.setAlphaComponent(accent, (int) (255 * selectionProgress)));
                cardRect.inset(dp(1), dp(1));
                canvas.drawRoundRect(cardRect, dp(9), dp(9), paint);
            }

            stickerRect.set(dp(10), dp(10), width - dp(10), dp(70));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.getColor(Theme.key_chat_outBubble, resourcesProvider));
            if (shape == StickerSettings.SHAPE_DEFAULT) {
                canvas.drawRect(stickerRect, paint);
            } else if (shape == StickerSettings.SHAPE_ROUNDED) {
                canvas.drawRoundRect(stickerRect, dp(6), dp(6), paint);
            } else {
                float radius = dp(SharedConfig.bubbleRadius);
                float nearRadius = radius / 3.0f;
                stickerPath.rewind();
                stickerPath.addRoundRect(
                        stickerRect,
                        new float[]{
                                radius, radius,
                                radius, radius,
                                radius, radius,
                                nearRadius, nearRadius
                        },
                        Path.Direction.CW
                );
                canvas.drawPath(stickerPath, paint);
            }

            String label = LocaleController.getString(shape == StickerSettings.SHAPE_DEFAULT
                    ? R.string.Default
                    : shape == StickerSettings.SHAPE_ROUNDED
                    ? R.string.FluxGramStickerShapeRounded
                    : R.string.FluxGramStickerShapeMessage);
            paint.setTextSize(dp(13));
            paint.setTypeface(selectionProgress >= 0.5f
                    ? AndroidUtilities.bold()
                    : AndroidUtilities.regular());
            paint.setColor(text);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, width / 2.0f, dp(102), paint);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(android.widget.RadioButton.class.getName());
            info.setCheckable(true);
            info.setChecked(isSelected());
            info.setText(LocaleController.getString(shape == StickerSettings.SHAPE_DEFAULT
                    ? R.string.Default
                    : shape == StickerSettings.SHAPE_ROUNDED
                    ? R.string.FluxGramStickerShapeRounded
                    : R.string.FluxGramStickerShapeMessage));
        }
    }
}
