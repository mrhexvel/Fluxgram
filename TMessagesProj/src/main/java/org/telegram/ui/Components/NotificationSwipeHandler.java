package org.telegram.ui.Components;

import android.view.MotionEvent;
import android.view.VelocityTracker;

final class NotificationSwipeHandler {

    interface Host {
        void onPress();

        float getOffset();

        int getHeight();

        void onDrag(float offset);

        void onRelease(boolean dismiss, boolean click);
    }

    private final Host host;
    private final int touchSlop;
    private final int minimumDistance;
    private final int minimumVelocity;
    private final int maximumVelocity;
    private VelocityTracker velocityTracker;
    private boolean active;
    private boolean dragging;
    private boolean moved;
    private boolean rejected;
    private float downX;
    private float downY;
    private float startOffset;

    NotificationSwipeHandler(Host host, int touchSlop, int minimumDistance,
            int minimumVelocity, int maximumVelocity) {
        this.host = host;
        this.touchSlop = touchSlop;
        this.minimumDistance = minimumDistance;
        this.minimumVelocity = minimumVelocity;
        this.maximumVelocity = maximumVelocity;
    }

    boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            cancel();
            active = true;
            downX = event.getRawX();
            downY = event.getRawY();
            host.onPress();
            startOffset = host.getOffset();
            velocityTracker = VelocityTracker.obtain();
            track(event);

            return true;
        }

        if (!active) {
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
            cancel();
            host.onRelease(false, false);

            return true;
        }

        track(event);
        float dx = event.getRawX() - downX;
        float dy = event.getRawY() - downY;

        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (!moved && dx * dx + dy * dy > touchSlop * touchSlop) {
                moved = true;
                dragging = dy < -touchSlop && Math.abs(dy) > Math.abs(dx);
                rejected = !dragging;
            }

            if (dragging && !rejected) {
                host.onDrag(Math.min(host.getHeight(), Math.max(0, startOffset - dy)));
            }
        }

        if (action == MotionEvent.ACTION_UP) {
            velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
            float velocityY = velocityTracker.getYVelocity();
            float distance = Math.max(0, -dy);
            float threshold = Math.max(minimumDistance, host.getHeight() * 0.28f);
            boolean dismiss = dragging && (distance >= threshold
                    || distance > touchSlop && velocityY < -minimumVelocity);
            boolean click = !moved;
            cancel();
            host.onRelease(dismiss, click);
        }

        return true;
    }

    private void track(MotionEvent event) {
        MotionEvent raw = MotionEvent.obtain(event);
        raw.setLocation(event.getRawX(), event.getRawY());
        velocityTracker.addMovement(raw);
        raw.recycle();
    }

    void cancel() {
        active = false;
        dragging = false;
        moved = false;
        rejected = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
}
