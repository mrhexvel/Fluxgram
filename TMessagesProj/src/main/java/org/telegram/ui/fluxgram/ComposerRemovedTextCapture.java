package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;
import android.os.Trace;
import android.text.Editable;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import org.telegram.messenger.plugins.PluginComposerRegistry;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;
import org.telegram.ui.Components.EditTextCaption;

public final class ComposerRemovedTextCapture implements PluginComposerRegistry.OverlayTextCapture {
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_CAPTURE_SIZE = 256;

    private final Picture picture;
    private final Rect ink;
    private final long revision;
    private final int start;
    private final int end;
    private final float baseline;
    private final ComposerTextLayer layer = new ComposerTextLayer();
    private boolean failed;

    private ComposerRemovedTextCapture(
            Picture picture, Rect ink, long revision, int start, int end, float baseline) {
        this.picture = picture;
        this.ink = ink;
        this.revision = revision;
        this.start = start;
        this.end = end;
        this.baseline = baseline;
    }

    public static ComposerRemovedTextCapture capture(
            EditTextCaption field, long revision, String expectedText, int start, int end) {
        Layout layout = field.getLayout();
        Editable content = field.getText();

        if (layout == null || content == null || expectedText == null
                || !TextUtils.equals(content, expectedText) || content.length() > MAX_TEXT_LENGTH
                || start < 0 || start >= end || end > content.length()
                || !ComposerNativeTextCapture.plainStyle(field)) {
            return null;
        }

        int line = layout.getLineForOffset(start);

        if (layout.getLineForOffset(end - 1) != line
                || layout.getParagraphDirection(line) != Layout.DIR_LEFT_TO_RIGHT
                || layout.getEllipsisCount(line) != 0) {
            return null;
        }

        String text = content.toString();
        TextPaint paint = field.getPaint();
        float rangeStart = layout.getPrimaryHorizontal(start);
        float rangeEnd = layout.getPrimaryHorizontal(end);
        float advance = paint.getRunAdvance(text, start, end,
                layout.getLineStart(line), layout.getLineEnd(line), false, end);

        if (rangeEnd <= rangeStart || Math.abs(rangeEnd - rangeStart - advance) > 0.25f
                || Math.abs(advance - paint.measureText(text, start, end)) > 0.25f) {
            return null;
        }

        Rect bounds = new Rect();
        paint.getTextBounds(text, start, end, bounds);

        if (bounds.isEmpty() || bounds.left < 0 || bounds.right > Math.ceil(advance)) {
            return null;
        }

        float originX = field.getCompoundPaddingLeft() - field.getScrollX();
        float originY = field.getBaseline() - layout.getLineBaseline(0) - field.getScrollY();
        Rect ink = new Rect(
                (int) Math.floor(originX + rangeStart),
                (int) Math.floor(originY + layout.getLineTop(line)),
                (int) Math.ceil(originX + rangeEnd),
                (int) Math.ceil(originY + layout.getLineBottom(line)));

        if (ink.left < field.getCompoundPaddingLeft()
                || ink.right > field.getWidth() - field.getCompoundPaddingRight()
                || ink.top < field.getTotalPaddingTop()
                || ink.bottom > field.getHeight() - field.getPaddingBottom()
                || ink.width() <= 0 || ink.height() <= 0
                || ink.width() > MAX_CAPTURE_SIZE || ink.height() > MAX_CAPTURE_SIZE) {
            return null;
        }

        Picture picture = new Picture();
        Canvas recording = picture.beginRecording(ink.width(), ink.height());
        TextPaint originalPaint = new TextPaint(paint);

        try {
            paint.setColor(field.getCurrentTextColor());
            recording.clipRect(0, 0, ink.width(), ink.height());
            recording.translate(originX - ink.left, originY - ink.top);
            layout.draw(recording);
        } finally {
            paint.set(originalPaint);
            picture.endRecording();
        }

        return new ComposerRemovedTextCapture(
                picture, ink, revision, start, end, originY + layout.getLineBaseline(line));
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public int getStart() {
        return start;
    }

    @Override
    public int getEnd() {
        return end;
    }

    @Override
    public float getLeft() {
        return ink.left;
    }

    @Override
    public float getTop() {
        return ink.top;
    }

    @Override
    public float getRight() {
        return ink.right;
    }

    @Override
    public float getBottom() {
        return ink.bottom;
    }

    @Override
    public float getBaseline() {
        return baseline;
    }

    @Override
    public boolean isValid() {
        return !failed;
    }

    @Override
    public void draw(Canvas canvas, ComposerTextPresentation presentation) {
        if (!isValid()) {
            return;
        }

        int save = canvas.save();
        Trace.beginSection("FGComposer.removedReplay");

        try {
            canvas.translate(ink.left, ink.top);
            ComposerNativeTextCapture.applyTransform(canvas, presentation);

            layer.save(canvas, ink.width(), ink.height(), presentation);

            if (presentation.alpha > 0) {
                canvas.drawPicture(picture);
            }
        } catch (Exception | LinkageError | AssertionError error) {
            failed = true;
            Log.e("FluxGramComposer", "Removed text replay failed", error);
        } finally {
            try {
                canvas.restoreToCount(save);
            } finally {
                Trace.endSection();
            }
        }
    }
}
