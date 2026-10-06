import java.util.function.Consumer;

public class NotificationTransitionCheck {

    public static void main(String[] args) {
        NotificationCardLayout layout = new NotificationCardLayout(new Context(), null);
        NotificationBlurBackground background = new NotificationBlurBackground();
        layout.setCustomBackground(background);
        Bulletin.Layout.Transition transition = layout.createTransition();
        int[] completions = new int[2];

        transition.animateEnter(layout, null, () -> completions[0]++, null, 0);
        SpringAnimation entrance = SpringAnimation.latest;
        layout.inOutOffset = 35;
        layout.transitionRunningEnter = true;

        transition.animateExit(layout, layout::onExitTransitionStart,
                () -> completions[1]++, null, 0);

        require(entrance.canceled, "Entrance still running during exit");
        require(layout.inOutOffset == 35, "Exit jumped from the interrupted position");
        require(completions[0] == 0, "Canceled entrance ran its completion");
        require(!layout.transitionRunningEnter, "Entrance state not cleared");
        require(background.frozen, "Live blur still capturing during exit");
        require(completions[1] == 0, "Exit completed before animation ended");
        require(layout.useCustomHideTransition(), "Default hide replaces the managed transition");

        ObjectAnimator exit = ObjectAnimator.latest;
        exit.finish();
        require(layout.inOutOffset == layout.getHeight(), "Exit stopped before leaving the screen");
        require(completions[1] == 1, "Exit completion must run once");
        layout.onDetach();
        require(completions[1] == 1, "Detach repeated exit completion");

        transition.animateEnter(layout, null, () -> completions[0]++, null, 0);
        layout.onDetach();
        require(SpringAnimation.latest.canceled, "Detached entrance kept running");
        require(completions[0] == 0, "Detached entrance ran completion");

        transition.animateExit(layout, layout::onExitTransitionStart,
                () -> completions[1]++, null, 0);
        exit = ObjectAnimator.latest;
        layout.onDetach();
        require(exit.canceled, "Detached exit kept running");
        require(completions[1] == 1, "Canceled exit ran completion");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

@interface SuppressLint {
    String[] value();
}

class Context {
}

class Drawable {
}

class Theme {
    interface ResourcesProvider {
    }
}

class NotificationBlurBackground extends Drawable {
    boolean frozen;

    void freeze() {
        frozen = true;
    }
}

class CubicBezierInterpolator {
    static final Object EASE_BOTH = new Object();
    static final Object EASE_OUT = new Object();
}

class OffsetProperty {
    void set(Bulletin.Layout layout, float value) {
        layout.inOutOffset = value;
    }
}

class Bulletin {
    boolean showing = true;
    boolean canHide = true;
    int hides;

    boolean isShowing() {
        return showing;
    }

    void hide() {
        showing = false;
        hides++;
    }

    void setCanHide(boolean value) {
        canHide = value;
    }

    static class Layout {
        static final OffsetProperty IN_OUT_OFFSET_Y = new OffsetProperty();
        static final OffsetProperty IN_OUT_OFFSET_Y2 = new OffsetProperty();
        boolean transitionRunningEnter;
        float inOutOffset;
        boolean top = true;
        Bulletin bulletin = new Bulletin();
        ClickListener onClickListener;

        interface ClickListener {
            void onClick(Layout layout);
        }

        public void setClickable(boolean value) {
        }

        public void setPressed(boolean value) {
        }

        public ViewParent getParent() {
            return null;
        }

        public boolean dispatchTouchEvent(MotionEvent event) {
            return false;
        }

        public boolean performClick() {
            return false;
        }

        interface Transition {
            void animateEnter(Layout layout, Runnable start, Runnable end,
                    Consumer<Float> update, int bottom);
            void animateExit(Layout layout, Runnable start, Runnable end,
                    Consumer<Float> update, int bottom);
        }

        Transition createTransition() {
            return null;
        }

        boolean useCustomHideTransition() {
            return false;
        }

        void setCustomBackground(Drawable drawable) {
        }
        protected void onExitTransitionStart() {
        }
        protected void onDetach() {
        }

        int getHeight() {
            return 100;
        }

        int getMeasuredHeight() {
            return getHeight();
        }

        float getTranslationY() {
            return -inOutOffset;
        }
    }

    static class TwoLineBackupLayout extends Layout {
        TwoLineBackupLayout(Context context, Theme.ResourcesProvider provider) {
        }
    }
}

class Animator {
}

class AnimatorListenerAdapter {
    public void onAnimationEnd(Animator animation) {
    }
}

class ObjectAnimator extends Animator {
    static ObjectAnimator latest;
    Bulletin.Layout layout;
    float destination;
    AnimatorListenerAdapter listener;
    boolean canceled;

    static ObjectAnimator ofFloat(Bulletin.Layout layout, OffsetProperty property, float value) {
        latest = new ObjectAnimator();
        latest.layout = layout;
        latest.destination = value;

        return latest;
    }

    void setDuration(long duration) {
    }

    void setInterpolator(Object interpolator) {
    }

    void addUpdateListener(Consumer<ObjectAnimator> listener) {
    }

    void removeAllUpdateListeners() {
    }

    void start() {
    }

    void addListener(AnimatorListenerAdapter value) {
        listener = value;
    }

    void removeAllListeners() {
        listener = null;
    }

    void cancel() {
        canceled = true;

        if (listener != null) {
            listener.onAnimationEnd(this);
        }
    }

    void finish() {
        layout.inOutOffset = destination;

        if (listener != null) {
            listener.onAnimationEnd(this);
        }
    }
}

class SpringAnimation {
    interface EndListener {
        void accept(SpringAnimation animation, boolean canceled, float value, float velocity);
    }

    interface UpdateListener {
        void accept(SpringAnimation animation, float value, float velocity);
    }

    static SpringAnimation latest;
    EndListener listener;
    boolean canceled;

    SpringAnimation(Bulletin.Layout layout, OffsetProperty property, float value) {
        latest = this;
    }

    SpringAnimation getSpring() {
        return this;
    }

    void setDampingRatio(float value) {
    }

    void setStiffness(float value) {
    }

    void addUpdateListener(UpdateListener listener) {
    }

    void start() {
    }

    void addEndListener(EndListener value) {
        listener = value;
    }

    void cancel() {
        canceled = true;

        if (listener != null) {
            listener.accept(this, true, 0, 0);
        }
    }
}
