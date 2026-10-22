package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ViewPagerActivity;

import java.util.List;

public final class AospWindowTransition {
    private static final float BASE_DURATION = 450.0f;

    private AospWindowTransition() {
    }

    public static int getDuration(Context context) {
        float scale = 1.0f;
        try {
            scale = Settings.Global.getFloat(
                    context.getContentResolver(),
                    Settings.Global.TRANSITION_ANIMATION_SCALE,
                    1.0f
            );
        } catch (Exception ignore) {
        }

        return Math.max(1, Math.round(BASE_DURATION * scale));
    }

    public static float getSlideDistance() {
        return AndroidUtilities.dp(96);
    }

    public static void apply(View foreground, View background, boolean open, float progress) {
        float clampedProgress = MathUtils.clamp(progress, 0.0f, 1.0f);
        float distance = getSlideDistance();
        float translation = Math.round(
                CubicBezierInterpolator.Emphasized.getInterpolation(clampedProgress) * distance
        );

        if (foreground != null) {
            foreground.setTranslationX(open ? distance - translation : translation - distance);
            foreground.setAlpha(open ? fade(clampedProgress, 50.0f) : 1.0f);
        }
        if (background != null) {
            background.setTranslationX(open ? -translation : translation);
            background.setAlpha(open ? 1.0f : 1.0f - fade(clampedProgress, 35.0f));
        }
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

    private static float fade(float progress, float delay) {
        return MathUtils.clamp((progress * BASE_DURATION - delay) / 83.0f, 0.0f, 1.0f);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public static final class EdgeExtension {
        private final RenderNode node = new RenderNode("aospWindowEdgeExtension");
        private boolean captured;

        public boolean isCaptured() {
            return captured;
        }

        public boolean capture(View view, boolean leftEdge) {
            int width = view.getWidth();
            int height = view.getHeight();
            if (width <= 0 || height <= 0) {
                return false;
            }

            node.setPosition(0, 0, 1, height);
            node.setPivotX(0.0f);
            node.setPivotY(0.0f);
            RecordingCanvas canvas = node.beginRecording(1, height);
            try {
                canvas.translate(leftEdge ? 0.0f : -(width - 1), 0.0f);
                view.draw(canvas);
            } finally {
                node.endRecording();
            }
            node.setUseCompositingLayer(true, null);
            captured = true;
            return true;
        }

        public void draw(Canvas canvas, float translationX, float width, float alpha) {
            if (!captured || width <= 0.0f || alpha <= 0.0f || !(canvas instanceof RecordingCanvas)) {
                return;
            }

            node.setScaleX(width);
            node.setTranslationX(translationX);
            node.setAlpha(alpha);
            ((RecordingCanvas) canvas).drawRenderNode(node);
        }

        public void release() {
            if (!captured) {
                return;
            }

            captured = false;
            node.setUseCompositingLayer(false, null);
            node.discardDisplayList();
        }
    }
}
