package android.graphics.drawable;

import android.graphics.Canvas;

public abstract class Drawable {
    public int getIntrinsicWidth() {
        return 24;
    }

    public int getIntrinsicHeight() {
        return 24;
    }

    public void setBounds(int left, int top, int right, int bottom) {
    }

    public void draw(Canvas canvas) {
    }
}
