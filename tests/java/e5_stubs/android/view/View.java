package android.view;

public class View {
    private final int width;
    private final int height;
    private int invalidations;

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

    public void invalidate() {
        invalidations++;
    }

    public int getInvalidations() {
        return invalidations;
    }
}
