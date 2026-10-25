package android.graphics;

public class Canvas {
    private final Bitmap bitmap;

    public Canvas(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void markVisible() {
        bitmap.markVisible();
    }
}
