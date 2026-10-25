package android.widget;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

public class ImageView {
    private final Resources resources;
    public Drawable drawable;
    public int resourceId;

    public ImageView(Resources resources) {
        this.resources = resources;
    }

    public Resources getResources() {
        return resources;
    }

    public void setImageDrawable(Drawable drawable) {
        this.drawable = drawable;
        resourceId = 0;
    }

    public void setImageResource(int resourceId) {
        drawable = null;
        this.resourceId = resourceId;
    }
}
