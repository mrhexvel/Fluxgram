public class NotificationSwipeCheck {

    public static void main(String[] args) {
        int[] clicks = new int[1];
        NotificationCardLayout card = card(clicks);
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        require(!card.bulletin.canHide, "Timer continued during touch");
        touch(card, MotionEvent.ACTION_MOVE, 100, 65, 0);
        require(card.inOutOffset == 35, "Card did not follow the finger");
        touch(card, MotionEvent.ACTION_UP, 100, 65, 0);
        require(card.bulletin.hides == 1 && clicks[0] == 0, "Swipe clicked or failed to dismiss");
        require(VelocityTracker.live == 0, "Velocity tracker leaked after swipe");

        card = card(clicks);
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        touch(card, MotionEvent.ACTION_MOVE, 100, 85, 0);
        touch(card, MotionEvent.ACTION_UP, 100, 85, 0);
        require(card.bulletin.hides == 0 && clicks[0] == 0, "Short swipe dismissed or clicked");
        require(!card.bulletin.canHide, "Timer resumed before return animation");
        ObjectAnimator returning = ObjectAnimator.latest;
        returning.finish();
        require(card.inOutOffset == 0 && card.bulletin.canHide, "Short swipe did not restore card");

        card = card(clicks);
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        touch(card, MotionEvent.ACTION_UP, 100, 85, -1200);
        require(card.bulletin.hides == 1, "Fast short upward swipe did not dismiss");

        card = card(clicks);
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        touch(card, MotionEvent.ACTION_UP, 140, 99, -1500);
        require(card.bulletin.hides == 0 && clicks[0] == 0, "Sideways gesture dismissed or clicked");

        card = card(clicks);
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        touch(card, MotionEvent.ACTION_MOVE, 100, 60, 0);
        touch(card, MotionEvent.ACTION_CANCEL, 100, 60, 0);
        ObjectAnimator.latest.finish();
        require(card.bulletin.hides == 0 && clicks[0] == 0, "Canceled gesture activated card");
        require(card.inOutOffset == 0 && card.bulletin.canHide, "Cancellation did not restore timer");

        card = card(clicks);
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        touch(card, MotionEvent.ACTION_POINTER_DOWN, 100, 100, 0);
        touch(card, MotionEvent.ACTION_UP, 100, 40, -2000);
        require(card.bulletin.hides == 0 && clicks[0] == 0, "Multi-touch activated card");

        card = card(clicks);
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        touch(card, MotionEvent.ACTION_UP, 102, 101, 0);
        require(clicks[0] == 1, "Tap did not invoke exactly one click");

        card = card(clicks);
        card.createTransition().animateEnter(card, null, null, null, 0);
        SpringAnimation entrance = SpringAnimation.latest;
        card.inOutOffset = 35;
        touch(card, MotionEvent.ACTION_DOWN, 100, 100, 0);
        require(entrance.canceled && card.inOutOffset == 35, "Touch jumped during entrance");
        touch(card, MotionEvent.ACTION_MOVE, 100, 85, 0);
        touch(card, MotionEvent.ACTION_UP, 100, 85, 0);
        returning = ObjectAnimator.latest;
        touch(card, MotionEvent.ACTION_DOWN, 100, 85, 0);
        require(returning.canceled && !card.bulletin.canHide, "New touch did not interrupt return");
        card.onExitTransitionStart();
        require(VelocityTracker.live == 0, "Exit leaked an active gesture");
        require(!card.bulletin.canHide, "Exit restarted the timer");
        card.onDetach();
    }

    private static NotificationCardLayout card(int[] clicks) {
        NotificationCardLayout card = new NotificationCardLayout(new Context(), null);
        card.onClickListener = view -> clicks[0]++;

        return card;
    }

    private static void touch(NotificationCardLayout card, int action, float x, float y, float velocity) {
        require(card.dispatchTouchEvent(new MotionEvent(action, x, y, velocity)), "Gesture escaped the card");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

class AndroidUtilities {
    static int dp(int value) {
        return value;
    }
}

class ViewParent {
    void requestDisallowInterceptTouchEvent(boolean value) {
    }
}

class ViewConfiguration {
    static ViewConfiguration get(Context context) {
        return new ViewConfiguration();
    }

    int getScaledTouchSlop() {
        return 8;
    }

    int getScaledMinimumFlingVelocity() {
        return 50;
    }

    int getScaledMaximumFlingVelocity() {
        return 8000;
    }
}

class MotionEvent {
    static final int ACTION_DOWN = 0;
    static final int ACTION_MOVE = 1;
    static final int ACTION_UP = 2;
    static final int ACTION_CANCEL = 3;
    static final int ACTION_POINTER_DOWN = 4;
    final int action;
    final float rawX;
    final float rawY;
    final float velocity;
    float x;
    float y;

    MotionEvent(int action, float x, float y, float velocity) {
        this.action = action;
        this.rawX = x;
        this.rawY = y;
        this.velocity = velocity;
        this.x = x - 5;
        this.y = y - 20;
    }

    static MotionEvent obtain(MotionEvent event) {
        return new MotionEvent(event.action, event.rawX, event.rawY, event.velocity);
    }

    int getActionMasked() {
        return action;
    }

    float getRawX() {
        return rawX;
    }

    float getRawY() {
        return rawY;
    }

    void setLocation(float x, float y) {
        this.x = x;
        this.y = y;
    }

    void recycle() {
    }
}

class VelocityTracker {
    static int live;
    float velocity;

    static VelocityTracker obtain() {
        live++;

        return new VelocityTracker();
    }

    void addMovement(MotionEvent event) {
        if (event.y != event.rawY || event.x != event.rawX) {
            throw new AssertionError("Velocity uses moving local coordinates");
        }

        velocity = event.velocity;
    }

    void computeCurrentVelocity(int units, int maxVelocity) {
    }

    float getYVelocity() {
        return velocity;
    }

    void recycle() {
        live--;
    }
}
