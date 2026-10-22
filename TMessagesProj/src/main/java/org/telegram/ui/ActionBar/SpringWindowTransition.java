package org.telegram.ui.ActionBar;

import android.view.View;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

public final class SpringWindowTransition {
    public static final float FINAL_VALUE = 1000.0f;
    public static final float STIFFNESS = 900.0f;
    public static final float DAMPING_RATIO = 1.0f;

    private SpringWindowTransition() {
    }

    public static void apply(View current, View previous, boolean open, float progress, float width) {
        float clampedProgress = MathUtils.clamp(progress, 0.0f, 1.0f);

        current.setAlpha(1.0f);
        previous.setAlpha(1.0f);
        current.setTranslationY(0.0f);
        previous.setTranslationY(0.0f);

        if (open) {
            current.setTranslationX((1.0f - clampedProgress) * width);
            previous.setTranslationX(-clampedProgress * 0.35f * width);
            setScale(current, AndroidUtilities.lerp(0.85f, 1.0f, clampedProgress));
            setScale(previous, 1.0f);
        } else {
            current.setTranslationX(-(1.0f - clampedProgress) * 0.35f * width);
            previous.setTranslationX(clampedProgress * width);
            setScale(current, 1.0f);
            setScale(previous, AndroidUtilities.lerp(1.0f, 0.85f, clampedProgress));
        }
    }

    public static int getScrimAlpha(boolean darkTheme, boolean open, float progress) {
        float clampedProgress = MathUtils.clamp(progress, 0.0f, 1.0f);
        float visibility = open ? clampedProgress : 1.0f - clampedProgress;
        return Math.round((darkTheme ? 0.8f : 0.2f) * visibility * 255.0f);
    }

    private static void setScale(View view, float scale) {
        view.setScaleX(scale);
        view.setScaleY(scale);
    }
}
