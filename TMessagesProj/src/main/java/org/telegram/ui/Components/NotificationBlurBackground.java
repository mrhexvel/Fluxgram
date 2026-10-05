package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public final class NotificationBlurBackground extends Drawable
        implements View.OnAttachStateChangeListener {

    private static final float SCALE = 8f;
    private static final long FRAME_INTERVAL_MS = 33;
    private final View source;
    private final View target;
    private final int color;
    private final int strokeAlpha;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final int[] sourceLocation = new int[2];
    private final int[] targetLocation = new int[2];
    private BitmapShader shader;
    private boolean disposed;
    private boolean frozen;
    private int frozenTargetX;
    private int frozenTargetY;
    private boolean attached;
    private boolean processing;
    private boolean failed;
    private long lastCapture;
    private int bitmapScreenX, bitmapScreenY;
    private final Runnable refresh = this::capture;
    private int alpha = 255;

    public NotificationBlurBackground(View source, View target, int color, int strokeColor) {
        this.source = source;
        this.target = target;
        this.color = color;
        this.strokeAlpha = android.graphics.Color.alpha(strokeColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1));
        strokePaint.setColor(strokeColor);
        target.addOnAttachStateChangeListener(this);
    }

    public void freeze() {
        if (frozen) {
            return;
        }

        frozen = true;
        target.removeCallbacks(refresh);
        target.getLocationOnScreen(targetLocation);
        frozenTargetX = targetLocation[0];
        frozenTargetY = targetLocation[1];
    }

    private void scheduleNext() {
        if (!attached || disposed || frozen || failed || processing) {
            return;
        }

        target.removeCallbacks(refresh);

        long delay = Math.max(0, FRAME_INTERVAL_MS - (SystemClock.uptimeMillis() - lastCapture));
        target.postDelayed(refresh, delay);
    }

    private void capture() {
        if (!attached || disposed || frozen || failed || processing) {
            return;
        }

        lastCapture = SystemClock.uptimeMillis();

        if (!target.isShown()
                || source.getWindowVisibility() != View.VISIBLE
                || getBounds().isEmpty()
                || source.getWidth() <= 0
                || source.getHeight() <= 0) {
            scheduleNext();

            return;
        }

        Bitmap snapshot = null;
        View overlay = null;
        float overlayAlpha = 1f;

        try {
            source.getLocationOnScreen(sourceLocation);
            target.getLocationOnScreen(targetLocation);

            int padding = AndroidUtilities.dp(24);
            int left =
                    Math.max(0, targetLocation[0] - sourceLocation[0] + getBounds().left - padding);
            int top =
                    Math.max(0, targetLocation[1] - sourceLocation[1] + getBounds().top - padding);
            int right =
                    Math.min(
                            source.getWidth(),
                            targetLocation[0] - sourceLocation[0] + getBounds().right + padding);
            int bottom =
                    Math.min(
                            source.getHeight(),
                            targetLocation[1] - sourceLocation[1] + getBounds().bottom + padding);

            if (right <= left || bottom <= top) {
                scheduleNext();

                return;
            }

            snapshot =
                    Bitmap.createBitmap(
                            Math.max(1, (int) Math.ceil((right - left) / SCALE)),
                            Math.max(1, (int) Math.ceil((bottom - top) / SCALE)),
                            Bitmap.Config.ARGB_8888);

            final int screenX = sourceLocation[0] + left;
            final int screenY = sourceLocation[1] + top;

            if (target.getParent() instanceof View) {
                overlay = (View) target.getParent();
                overlayAlpha = overlay.getAlpha();
                overlay.setAlpha(0f);
            }

            Canvas canvas = new Canvas(snapshot);
            canvas.drawColor(color);
            canvas.scale(1f / SCALE, 1f / SCALE);
            canvas.translate(-left, -top);
            source.draw(canvas);

            final Bitmap result = snapshot;
            processing = true;

            Utilities.globalQueue.postRunnable(
                    () -> {
                        boolean success = false;

                        try {
                            Utilities.stackBlurBitmap(
                                    result, Math.max(2, AndroidUtilities.dp(20) / 8));
                            success = true;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }

                        final boolean ready = success;

                        AndroidUtilities.runOnUIThread(
                                () -> {
                                    processing = false;

                                    if (disposed || frozen || !ready) {
                                        result.recycle();
                                        failed = !ready;

                                        return;
                                    }

                                    bitmapScreenX = screenX;
                                    bitmapScreenY = screenY;

                                    shader =
                                            new BitmapShader(
                                                    result,
                                                    Shader.TileMode.CLAMP,
                                                    Shader.TileMode.CLAMP);
                                    imagePaint.setShader(shader);
                                    target.invalidate();
                                    scheduleNext();
                                });
                    });
        } catch (Exception e) {
            FileLog.e(e);
            failed = true;
            processing = false;

            if (snapshot != null) {
                snapshot.recycle();
            }
        } finally {
            if (overlay != null) {
                overlay.setAlpha(overlayAlpha);
            }
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        rect.set(getBounds());

        float radius = AndroidUtilities.dp(20);

        if (shader != null) {
            if (frozen) {
                targetLocation[0] = frozenTargetX;
                targetLocation[1] = frozenTargetY;
            } else {
                target.getLocationOnScreen(targetLocation);
            }

            matrix.setScale(SCALE, SCALE);
            matrix.postTranslate(
                    bitmapScreenX - targetLocation[0], bitmapScreenY - targetLocation[1]);
            shader.setLocalMatrix(matrix);
            imagePaint.setAlpha(alpha);
            canvas.drawRoundRect(rect, radius, radius, imagePaint);
        }

        tintPaint.setColor(ColorUtils.setAlphaComponent(color, shader == null ? 255 : 185));
        tintPaint.setAlpha(tintPaint.getAlpha() * alpha / 255);
        canvas.drawRoundRect(rect, radius, radius, tintPaint);
        rect.inset(strokePaint.getStrokeWidth() / 2f, strokePaint.getStrokeWidth() / 2f);
        strokePaint.setAlpha(strokeAlpha * alpha / 255);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        target.invalidate();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        attached = true;
        scheduleNext();
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        disposed = true;
        attached = false;
        target.removeCallbacks(refresh);
        imagePaint.setShader(null);
        shader = null;
        target.removeOnAttachStateChangeListener(this);
    }
}
