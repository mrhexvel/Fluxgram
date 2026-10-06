package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.core.util.Consumer;
import androidx.dynamicanimation.animation.SpringAnimation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public final class NotificationCardLayout extends Bulletin.TwoLineBackupLayout {

    private final CardTransition transition = new CardTransition();
    private NotificationBlurBackground blurBackground;
    private final NotificationSwipeHandler swipeHandler;
    private ObjectAnimator returnAnimation;

    public NotificationCardLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        setClickable(true);

        ViewConfiguration configuration = ViewConfiguration.get(context);
        swipeHandler = new NotificationSwipeHandler(new NotificationSwipeHandler.Host() {
            @Override
            public void onPress() {
                transition.cancel();
                cancelReturn();
                transitionRunningEnter = false;
                setPressed(true);
                bulletin.setCanHide(false);
                disallowParentInterception(true);
            }

            @Override
            public float getOffset() {
                return inOutOffset;
            }

            @Override
            public int getHeight() {
                return NotificationCardLayout.this.getHeight();
            }

            @Override
            public void onDrag(float offset) {
                IN_OUT_OFFSET_Y2.set(NotificationCardLayout.this, offset);
            }

            @Override
            public void onRelease(boolean dismiss, boolean click) {
                setPressed(false);
                disallowParentInterception(false);

                if (bulletin == null || !bulletin.isShowing()) {
                    return;
                }

                if (dismiss) {
                    bulletin.hide();

                    return;
                }

                if (click) {
                    performClick();
                }

                if (bulletin != null && bulletin.isShowing()) {
                    returnToPosition();
                }
            }
        }, configuration.getScaledTouchSlop(), AndroidUtilities.dp(24),
                Math.max(configuration.getScaledMinimumFlingVelocity(), AndroidUtilities.dp(900)),
                configuration.getScaledMaximumFlingVelocity());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!top) {
            return super.dispatchTouchEvent(event);
        }

        if (bulletin == null || !bulletin.isShowing()) {
            return true;
        }

        return swipeHandler.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();

        if (onClickListener != null && bulletin != null && bulletin.isShowing()) {
            onClickListener.onClick(this);

            return true;
        }

        return false;
    }

    private void disallowParentInterception(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void cancelReturn() {
        if (returnAnimation != null) {
            returnAnimation.removeAllListeners();
            returnAnimation.cancel();
            returnAnimation = null;
        }
    }

    private void returnToPosition() {
        cancelReturn();

        if (inOutOffset == 0) {
            bulletin.setCanHide(true);

            return;
        }

        returnAnimation = ObjectAnimator.ofFloat(this, IN_OUT_OFFSET_Y2, 0);
        returnAnimation.setDuration(200);
        returnAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        returnAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                returnAnimation = null;

                if (bulletin != null && bulletin.isShowing()) {
                    bulletin.setCanHide(true);
                }
            }
        });
        returnAnimation.start();
    }

    private void cancelInteraction() {
        swipeHandler.cancel();
        cancelReturn();
        setPressed(false);
        disallowParentInterception(false);
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
        cancelInteraction();
        transition.cancel();
        transitionRunningEnter = false;

        if (blurBackground != null) {
            blurBackground.freeze();
        }

        super.onExitTransitionStart();
    }

    @Override
    protected void onDetach() {
        cancelInteraction();
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
