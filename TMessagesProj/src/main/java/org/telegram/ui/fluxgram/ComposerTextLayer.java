package org.telegram.ui.fluxgram;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Picture;

import org.telegram.messenger.Utilities;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;

final class ComposerTextLayer {
    private static final int BLUR_PADDING = (int) ComposerTextPresentation.MAX_BLUR_RADIUS + 1;

    private Paint paint;
    private Bitmap sourceBitmap;
    private Bitmap blurredBitmap;
    private int tintColor;
    private float tintAmount = -1;
    private int blurRadius;

    void prepare(Picture picture, int width, int height, ComposerTextPresentation presentation) {
        if (presentation.blurRadius <= 0) {
            return;
        }

        if (sourceBitmap == null) {
            sourceBitmap = Bitmap.createBitmap(
                    width + BLUR_PADDING * 2, height + BLUR_PADDING * 2, Bitmap.Config.ARGB_8888);
            sourceBitmap.setDensity(Bitmap.DENSITY_NONE);
            Canvas canvas = new Canvas(sourceBitmap);
            canvas.translate(BLUR_PADDING, BLUR_PADDING);
            canvas.drawPicture(picture);
        }

        int requestedRadius = Math.max(1, Math.round(presentation.blurRadius));

        if (blurredBitmap == null) {
            blurredBitmap = Bitmap.createBitmap(
                    sourceBitmap.getWidth(), sourceBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            blurredBitmap.setDensity(Bitmap.DENSITY_NONE);
        }

        if (blurRadius != requestedRadius) {
            blurredBitmap.eraseColor(Color.TRANSPARENT);
            new Canvas(blurredBitmap).drawBitmap(sourceBitmap, 0, 0, null);
            Utilities.stackBlurBitmap(blurredBitmap, requestedRadius);
            blurRadius = requestedRadius;
        }
    }

    void draw(Canvas canvas, Picture picture, int width, int height, ComposerTextPresentation presentation) {
        if (presentation.alpha >= 1 && presentation.tintAmount <= 0 && presentation.blurRadius <= 0) {
            canvas.drawPicture(picture);
            return;
        }

        configure(presentation);

        if (presentation.blurRadius > 0) {
            canvas.drawBitmap(blurredBitmap, -BLUR_PADDING, -BLUR_PADDING, paint);
        } else {
            canvas.saveLayer(0, 0, width, height, paint);
            canvas.drawPicture(picture);
        }
    }

    void release() {
        sourceBitmap = null;
        blurredBitmap = null;
        blurRadius = 0;
    }

    private void configure(ComposerTextPresentation presentation) {
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
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
    }
}
