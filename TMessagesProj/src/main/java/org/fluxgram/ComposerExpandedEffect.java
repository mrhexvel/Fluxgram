package org.fluxgram;

import android.graphics.Canvas;

public interface ComposerExpandedEffect extends ComposerEffect {
    void drawExpanded(Canvas canvas, ComposerGeometry geometry, ComposerExpandedBounds bounds) throws Exception;
}
