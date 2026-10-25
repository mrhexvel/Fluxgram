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
    private boolean visible = true;

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
        Bitmap bitmap = new Bitmap(width, height);

        bitmap.visible = false;

        return bitmap;
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

    public void markVisible() {
        visible = true;
    }

    public void markTransparent() {
        visible = false;
    }

    public boolean hasVisiblePixels() {
        return visible;
    }

    public void getPixels(
            int[] pixels,
            int offset,
            int stride,
            int x,
            int y,
            int width,
            int height) {
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                pixels[offset + row * stride + column] = visible ? 0xff000000 : 0;
            }
        }
    }
}
