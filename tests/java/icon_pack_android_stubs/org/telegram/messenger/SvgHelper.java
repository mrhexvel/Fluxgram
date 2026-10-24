package org.telegram.messenger;

import android.graphics.drawable.Drawable;

public final class SvgHelper {
    public static int calls;

    public static final class SvgDrawable extends Drawable {
    }

    public static SvgDrawable getDrawable(String xml) {
        calls++;

        if (xml.contains("runtime")) {
            throw new IllegalStateException();
        }
        if (xml.contains("out-of-memory")) {
            throw new OutOfMemoryError();
        }
        if (xml == null || xml.contains("unsupported")) {
            return null;
        }

        return new SvgDrawable();
    }
}
