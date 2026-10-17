package org.fluxgram;

import android.graphics.Canvas;

/** An additive effect that may also draw in a bounded area around the composer field. */
public interface ComposerExpandedEffect extends ComposerEffect {
    /**
     * Draws on the main thread in field-local pixel coordinates within {@code bounds}.
     * The callback-scoped canvas is clipped to those bounds and must not be retained.
     */
    void drawExpanded(Canvas canvas, ComposerGeometry geometry, ComposerExpandedBounds bounds) throws Exception;
}
