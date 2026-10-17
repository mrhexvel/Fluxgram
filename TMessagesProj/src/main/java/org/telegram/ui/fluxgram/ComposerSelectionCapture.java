package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.RectF;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;

import org.telegram.messenger.plugins.PluginComposerRegistry;
import org.fluxgram.ComposerSelectionPresentation;
import org.fluxgram.ComposerSelectionSnapshot;
import org.telegram.ui.Components.EditTextCaption;

final class ComposerSelectionCapture implements PluginComposerRegistry.SelectionCapture {
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_LINES = 16;
    private static final int MAX_CAPTURE_WIDTH = 512;
    private static final int MAX_CAPTURE_HEIGHT = 256;

    private final EditTextCaption field;
    private final Layout layout;
    private final String expectedText;
    private final int selectionStart;
    private final int selectionEnd;
    private final int width;
    private final int height;
    private final int fieldWidth;
    private final int fieldHeight;
    private final int scrollX;
    private final int scrollY;
    private final float originX;
    private final float originY;
    private final float left;
    private final float top;
    private final float right;
    private final float bottom;
    private final ComposerSelectionSnapshot snapshot;
    private final ComposerTextLayer layer = new ComposerTextLayer();
    private Picture picture;

    static ComposerSelectionCapture capture(EditTextCaption field, long revision) {
        Layout layout = field.getLayout();
        Editable content = field.getText();
        int start = Math.min(field.getSelectionStart(), field.getSelectionEnd());
        int end = Math.max(field.getSelectionStart(), field.getSelectionEnd());

        if (layout == null || content == null || content.length() > MAX_TEXT_LENGTH
                || start < 0 || start >= end || end > content.length()
                || field.getWidth() <= 0 || field.getHeight() <= 0) {
            return null;
        }

        int firstLine = layout.getLineForOffset(start);
        int lastLine = layout.getLineForOffset(Math.max(start, end - 1));
        int lineCount = lastLine - firstLine + 1;

        if (lineCount < 1 || lineCount > MAX_LINES) {
            return null;
        }

        float originX = field.getCompoundPaddingLeft() - field.getScrollX();
        float offsetY = field.getOffsetY();
        float originY = field.getBaseline() - layout.getLineBaseline(0) - field.getScrollY() + offsetY;
        Path path = new Path();
        layout.getSelectionPath(start, end, path);
        path.offset(originX, originY);
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        float left = Math.max(field.getCompoundPaddingLeft(), (float) Math.floor(bounds.left));
        float top = Math.max(Math.max(0, field.getTotalPaddingTop() + offsetY),
                (float) Math.floor(bounds.top));
        float right = Math.min(field.getWidth() - field.getCompoundPaddingRight(),
                (float) Math.ceil(bounds.right));
        float bottom = Math.min(
                Math.min(field.getHeight(), field.getHeight() - field.getPaddingBottom() + offsetY),
                (float) Math.ceil(bounds.bottom));
        int width = Math.max(0, (int) (right - left));
        int height = Math.max(0, (int) (bottom - top));

        if (width <= 0 || width > MAX_CAPTURE_WIDTH
                || height <= 0 || height > MAX_CAPTURE_HEIGHT) {
            return null;
        }

        Picture picture = new Picture();
        Canvas recording = picture.beginRecording(width, height);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);

        try {
            recording.translate(-left, -top);
            recording.drawPath(path, paint);
        } finally {
            picture.endRecording();
        }

        return new ComposerSelectionCapture(field, layout, content.toString(), start, end,
                width, height, originX, originY, left, top, right, bottom,
                new ComposerSelectionSnapshot(
                        revision, start, end, lineCount, left, top, right, bottom), picture);
    }

    private ComposerSelectionCapture(EditTextCaption field, Layout layout, String expectedText,
            int selectionStart, int selectionEnd, int width, int height,
            float originX, float originY, float left, float top, float right, float bottom,
            ComposerSelectionSnapshot snapshot, Picture picture) {
        this.field = field;
        this.layout = layout;
        this.expectedText = expectedText;
        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
        this.width = width;
        this.height = height;
        this.fieldWidth = field.getWidth();
        this.fieldHeight = field.getHeight();
        this.scrollX = field.getScrollX();
        this.scrollY = field.getScrollY();
        this.originX = originX;
        this.originY = originY;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.snapshot = snapshot;
        this.picture = picture;
    }

    ComposerSelectionSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public long getRevision() {
        return snapshot.revision;
    }

    @Override
    public int getStart() {
        return selectionStart;
    }

    @Override
    public int getEnd() {
        return selectionEnd;
    }

    @Override
    public float getLeft() {
        return left;
    }

    @Override
    public float getTop() {
        return top;
    }

    @Override
    public float getRight() {
        return right;
    }

    @Override
    public float getBottom() {
        return bottom;
    }

    @Override
    public float getBaseline() {
        return bottom;
    }

    @Override
    public boolean isValid() {
        return picture != null && field.getLayout() == layout && field.getWidth() == fieldWidth
                && field.getHeight() == fieldHeight && field.getScrollX() == scrollX
                && field.getScrollY() == scrollY && field.getSelectionStart() >= 0
                && Math.min(field.getSelectionStart(), field.getSelectionEnd()) == selectionStart
                && Math.max(field.getSelectionStart(), field.getSelectionEnd()) == selectionEnd
                && TextUtils.equals(field.getText(), expectedText)
                && originX == field.getCompoundPaddingLeft() - field.getScrollX()
                && originY == field.getBaseline() - layout.getLineBaseline(0)
                - field.getScrollY() + field.getOffsetY();
    }

    @Override
    public void draw(Canvas canvas, ComposerSelectionPresentation presentation) {
        if (!isValid() || presentation.alpha <= 0) {
            return;
        }

        layer.prepare(picture, width, height, presentation);
        int save = canvas.save();

        try {
            canvas.translate(left, top);
            ComposerNativeTextCapture.applyTransform(canvas, presentation);
            layer.draw(canvas, picture, width, height, presentation);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Override
    public void release() {
        picture = null;
        layer.release();
    }
}
