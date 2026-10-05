package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.util.Consumer;
import androidx.dynamicanimation.animation.SpringAnimation;

import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public final class NotificationCardLayout extends Bulletin.TwoLineBackupLayout {

    private final CardTransition transition = new CardTransition();
    private NotificationBlurBackground blurBackground;

    public NotificationCardLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    @Override
    public void setCustomBackground(Drawable drawable) {
        super.setCustomBackground(drawable);

        blurBackground = drawable instanceof NotificationBlurBackground
                ? (NotificationBlurBackground) drawable : null;
    }

    @Override
    public Transition createTransition() {
        return transition;
    }

    @Override
    public boolean useCustomHideTransition() {
        return true;
    }

    @Override
    protected void onExitTransitionStart() {
        transition.cancel();
        transitionRunningEnter = false;

        if (blurBackground != null) {
            blurBackground.freeze();
        }

        super.onExitTransitionStart();
    }

    @Override
    protected void onDetach() {
        transition.cancel();
        super.onDetach();
    }

    private static final class CardTransition implements Transition {

        private SpringAnimation enterAnimation;
        private ObjectAnimator exitAnimation;

        private void cancel() {
            if (enterAnimation != null) {
                enterAnimation.cancel();
                enterAnimation = null;
            }

            if (exitAnimation != null) {
                exitAnimation.removeAllListeners();
                exitAnimation.removeAllUpdateListeners();
                exitAnimation.cancel();
                exitAnimation = null;
            }
        }

        @Override
        public void animateEnter(Bulletin.Layout layout, Runnable startAction, Runnable endAction,
                Consumer<Float> onUpdate, int bottomOffset) {
            cancel();
            IN_OUT_OFFSET_Y2.set(layout, (float) layout.getMeasuredHeight());

            enterAnimation = new SpringAnimation(layout, IN_OUT_OFFSET_Y, 0);
            enterAnimation.getSpring().setDampingRatio(0.8f);
            enterAnimation.getSpring().setStiffness(400f);
            enterAnimation.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled && endAction != null) {
                    endAction.run();
                }
            });

            if (onUpdate != null) {
                onUpdate.accept(layout.getTranslationY());
                enterAnimation.addUpdateListener((animation, value, velocity) ->
                        onUpdate.accept(layout.getTranslationY()));
            }

            if (startAction != null) {
                startAction.run();
            }

            enterAnimation.start();
        }

        @Override
        public void animateExit(Bulletin.Layout layout, Runnable startAction, Runnable endAction,
                Consumer<Float> onUpdate, int bottomOffset) {
            cancel();

            if (startAction != null) {
                startAction.run();
            }

            exitAnimation = ObjectAnimator.ofFloat(layout, IN_OUT_OFFSET_Y2, layout.getHeight());
            exitAnimation.setDuration(240);
            exitAnimation.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            exitAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    exitAnimation = null;

                    if (endAction != null) {
                        endAction.run();
                    }
                }
            });

            if (onUpdate != null) {
                exitAnimation.addUpdateListener(animation ->
                        onUpdate.accept(layout.getTranslationY()));
            }

            exitAnimation.start();
        }
    }
}
