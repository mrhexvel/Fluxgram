package android.graphics;

public class Bitmap {
    public static Bitmap lastCreated;

    private final int width;
    private final int height;
    private boolean recycled;

    public Bitmap(int width, int height) {
        this.width = width;
        this.height = height;
        lastCreated = this;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void recycle() {
        recycled = true;
    }

    public boolean isRecycled() {
        return recycled;
    }
}
