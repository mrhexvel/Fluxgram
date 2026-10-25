package android.graphics;

public class Bitmap {
    public enum Config {
        ARGB_8888
    }

    public static Bitmap lastCreated;

    private final int width;
    private final int height;
    private boolean recycled;
    private int density;

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

    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(width, height);
    }

    public void setDensity(int density) {
        this.density = density;
    }

    public int getDensity() {
        return density;
    }

    public void recycle() {
        recycled = true;
    }

    public boolean isRecycled() {
        return recycled;
    }
}
