package org.telegram.ui.ActionBar;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ViewPagerActivity;

import java.util.List;

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

    public static Drawable getBackground(List<BaseFragment> fragments, BaseFragment fragment) {
        if (fragments != null && fragments.size() > 1) {
            fragment = fragments.get(fragments.size() - 2);
        }
        while (fragment instanceof ViewPagerActivity) {
            BaseFragment visibleFragment = ((ViewPagerActivity) fragment).getCurrentVisibleFragment();
            if (visibleFragment == null || visibleFragment == fragment) {
                break;
            }
            fragment = visibleFragment;
        }

        if (fragment instanceof ProfileActivity) {
            return new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, fragment.getResourceProvider()));
        }

        View view = fragment != null ? fragment.fragmentView : null;
        Drawable background = view != null ? view.getBackground() : null;
        if (background instanceof ColorDrawable && Color.alpha(((ColorDrawable) background).getColor()) == 0) {
            background = null;
        }
        if (background != null) {
            return background;
        }

        return new ColorDrawable(Theme.getColor(
                Theme.key_windowBackgroundGray,
                fragment != null ? fragment.getResourceProvider() : null
        ));
    }

    public static void drawBackground(Canvas canvas, Drawable drawable, int width, int height, Rect bounds) {
        if (drawable == null) {
            return;
        }

        drawable.copyBounds(bounds);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        drawable.setBounds(bounds);
    }

    private static void setScale(View view, float scale) {
        view.setScaleX(scale);
        view.setScaleY(scale);
    }
}
