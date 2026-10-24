package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;

public class BitmapDrawable extends Drawable {
    public static boolean throwRuntime;
    public static boolean throwOutOfMemory;

    private final Bitmap bitmap;

    public BitmapDrawable(Resources resources, Bitmap bitmap) {
        if (throwRuntime) {
            throwRuntime = false;
            throw new IllegalStateException();
        }
        if (throwOutOfMemory) {
            throwOutOfMemory = false;
            throw new OutOfMemoryError();
        }
        if (resources == null || bitmap == null) {
            throw new IllegalArgumentException();
        }

        this.bitmap = bitmap;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
}
