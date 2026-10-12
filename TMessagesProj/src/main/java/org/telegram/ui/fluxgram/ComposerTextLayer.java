package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import org.telegram.messenger.plugins.api.ComposerTextPresentation;

final class ComposerTextLayer {
    private Paint paint;
    private int tintColor;
    private float tintAmount = -1;

    void save(Canvas canvas, int width, int height, ComposerTextPresentation presentation) {
        if (presentation.alpha >= 1 && presentation.tintAmount <= 0) {
            return;
        }

        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        paint.setAlpha(Math.round(255 * presentation.alpha));

        if (presentation.tintAmount <= 0) {
            paint.setColorFilter(null);
            tintColor = 0;
            tintAmount = 0;
        } else if (tintColor != presentation.tintColor || tintAmount != presentation.tintAmount) {
            float original = 1 - presentation.tintAmount;
            float red = Color.red(presentation.tintColor) * presentation.tintAmount;
            float green = Color.green(presentation.tintColor) * presentation.tintAmount;
            float blue = Color.blue(presentation.tintColor) * presentation.tintAmount;
            paint.setColorFilter(new ColorMatrixColorFilter(new float[]{
                    original, 0, 0, 0, red,
                    0, original, 0, 0, green,
                    0, 0, original, 0, blue,
                    0, 0, 0, 1, 0
            }));
            tintColor = presentation.tintColor;
            tintAmount = presentation.tintAmount;
        }

        canvas.saveLayer(0, 0, width, height, paint);
    }
}
