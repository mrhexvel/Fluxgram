package android.view;

public class View {
    private final int width;
    private final int height;
    private int invalidations;
    private int scrollX;
    private int scrollY;

    public View(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getScrollX() {
        return scrollX;
    }

    public int getScrollY() {
        return scrollY;
    }

    public void scrollTo(int x, int y) {
        scrollX = x;
        scrollY = y;
    }

    public void invalidate() {
        invalidations++;
    }

    public int getInvalidations() {
        return invalidations;
    }
}
