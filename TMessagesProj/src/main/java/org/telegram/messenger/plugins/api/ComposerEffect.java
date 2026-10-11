package org.telegram.messenger.plugins.api;

import android.graphics.Canvas;

public interface ComposerEffect extends AutoCloseable {
    interface Factory {
        ComposerEffect create(ComposerSurface surface) throws Exception;
    }

    void onInput(ComposerInput input) throws Exception;

    boolean onFrame(long frameTimeNanos) throws Exception;

    void draw(Canvas canvas, ComposerGeometry geometry) throws Exception;

    @Override
    void close() throws Exception;
}
