package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Trace;
import android.util.Log;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.SuggestionSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;

import org.telegram.messenger.plugins.PluginComposerRegistry;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;
import org.telegram.ui.Components.EditTextCaption;

import java.util.function.Consumer;

public final class ComposerNativeTextCapture implements PluginComposerRegistry.TextCapture {
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_CAPTURE_SIZE = 256;

    private final EditTextCaption field;
    private final Layout layout;
    private final Picture picture;
    private final Rect ink;
    private final long revision;
    private final int start;
    private final int end;
    private final String text;
    private final int width;
    private final int height;
    private final int color;
    private final int baseline;
    private final int fieldBaseline;
    private final int line;
    private final int leftPadding;
    private final float textSize;
    private final Typeface typeface;
    private final int selectionStart;
    private final int selectionEnd;
    private final int composingStart;
    private final int composingEnd;
    private final ComposerTextLayer layer = new ComposerTextLayer();
    private boolean failed;

    private ComposerNativeTextCapture(EditTextCaption field, Layout layout, Picture picture, Rect ink,
            ComposerTextCandidate candidate, String text, int line, int baseline) {
        this.field = field;
        this.layout = layout;
        this.picture = picture;
        this.ink = ink;
        revision = candidate.revision;
        start = candidate.start;
        end = candidate.end;
        this.text = text;
        width = field.getWidth();
        height = field.getHeight();
        color = field.getCurrentTextColor();
        this.baseline = baseline;
        fieldBaseline = field.getBaseline();
        this.line = line;
        leftPadding = field.getCompoundPaddingLeft();
        textSize = field.getTextSize();
        typeface = field.getTypeface();
        selectionStart = field.getSelectionStart();
        selectionEnd = field.getSelectionEnd();
        composingStart = BaseInputConnection.getComposingSpanStart(field.getText());
        composingEnd = BaseInputConnection.getComposingSpanEnd(field.getText());
    }

    public static ComposerNativeTextCapture capture(
            EditTextCaption field, ComposerTextCandidate candidate, String expectedText) {
        return capture(field, candidate, expectedText, false);
    }

    static ComposerNativeTextCapture capture(
            EditTextCaption field, ComposerTextCandidate candidate, String expectedText,
            boolean allowCursorAtBoundary) {
        Layout layout = field.getLayout();
        CharSequence content = field.getText();

        if (layout == null || candidate == null || content == null || expectedText == null
                || !TextUtils.equals(content, expectedText) || content.length() > MAX_TEXT_LENGTH
                || candidate.start < 0 || candidate.start >= candidate.end || candidate.end > content.length()
                || !plainStyle(field)) {
            return null;
        }

        int line = layout.getLineForOffset(candidate.start);

        if (layout.getLineForOffset(candidate.end - 1) != line
                || layout.getParagraphDirection(line) != Layout.DIR_LEFT_TO_RIGHT
                || layout.getEllipsisCount(line) != 0) {
            return null;
        }

        int selectionStart = field.getSelectionStart();
        int selectionEnd = field.getSelectionEnd();

        if (selectionStart != selectionEnd && selectionStart < candidate.end && selectionEnd > candidate.start) {
            return null;
        }

        String text = content.toString();
        TextPaint paint = field.getPaint();
        float rangeStart = layout.getPrimaryHorizontal(candidate.start);
        float rangeEnd = layout.getPrimaryHorizontal(candidate.end);
        int contextStart = layout.getLineStart(line);
        int contextEnd = layout.getLineEnd(line);
        float advance = paint.getRunAdvance(text, candidate.start, candidate.end,
                contextStart, contextEnd, false, candidate.end);

        if (rangeEnd <= rangeStart || !allowCursorAtBoundary
                && (Math.abs(rangeEnd - rangeStart - advance) > 0.25f
                || Math.abs(advance - paint.measureText(text, candidate.start, candidate.end)) > 0.25f)) {
            return null;
        }

        Rect bounds = new Rect();
        paint.getTextBounds(text, candidate.start, candidate.end, bounds);

        if (bounds.isEmpty() || bounds.left < 0 || bounds.right > Math.ceil(advance)) {
            return null;
        }

        int leftPadding = field.getCompoundPaddingLeft();
        float originX = leftPadding - field.getScrollX();
        float originY = field.getBaseline() - layout.getLineBaseline(0) - field.getScrollY();
        int baseline = Math.round(originY + layout.getLineBaseline(line));
        Rect ink = new Rect(
                (int) Math.floor(originX + rangeStart + bounds.left),
                baseline + bounds.top,
                (int) Math.ceil(originX + rangeStart + bounds.right),
                baseline + bounds.bottom);

        if (selectionStart == selectionEnd && selectionStart >= 0) {
            float cursor = leftPadding + layout.getPrimaryHorizontal(selectionStart);

            boolean overlaps = allowCursorAtBoundary
                    ? selectionStart == candidate.start || cursor > ink.left && cursor < ink.right
                    : cursor >= ink.left - 1 && cursor <= ink.right + 1;

            if (overlaps) {
                return null;
            }
        }

        float offsetY = field.getOffsetY();

        if (ink.left < leftPadding || ink.right > field.getWidth() - field.getCompoundPaddingRight()
                || ink.top + offsetY < field.getTotalPaddingTop()
                || ink.bottom + offsetY > field.getHeight() - field.getPaddingBottom()
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

        return new ComposerNativeTextCapture(field, layout, picture, ink, candidate, text, line, baseline);
    }

    static boolean plainStyle(EditTextCaption field) {
        TextPaint paint = field.getPaint();

        if (field.getScrollX() != 0 || field.getScrollY() != 0
                || field.getTransformationMethod() != null
                || field.getLayoutDirection() != View.LAYOUT_DIRECTION_LTR
                || !TextUtils.isEmpty(field.getCaption()) || paint.getShader() != null
                || paint.getColorFilter() != null || paint.getMaskFilter() != null
                || field.getShadowRadius() != 0 || paint.getXfermode() != null
                || paint.getStyle() != Paint.Style.FILL || paint.getTextSkewX() != 0
                || paint.getTextScaleX() != 1 || paint.getLetterSpacing() != 0
                || paint.baselineShift != 0 || paint.bgColor != 0 || paint.isStrikeThruText()
                || !TextUtils.isEmpty(paint.getFontFeatureSettings())
                || Build.VERSION.SDK_INT >= 26 && !TextUtils.isEmpty(paint.getFontVariationSettings())
                || field.getTypeface() != null && field.getTypeface().isItalic()) {
            return false;
        }

        CharSequence text = field.getText();

        if (text instanceof Spanned) {
            Spanned spans = (Spanned) text;

            if (spans.getSpans(0, text.length(), ParagraphStyle.class).length != 0) {
                return false;
            }

            for (CharacterStyle style : spans.getSpans(0, text.length(), CharacterStyle.class)) {
                if (!(style instanceof UnderlineSpan) && !(style instanceof SuggestionSpan)) {
                    return false;
                }
            }
        }

        return true;
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
        return ink.top + field.getOffsetY();
    }

    @Override
    public float getRight() {
        return ink.right;
    }

    @Override
    public float getBottom() {
        return ink.bottom + field.getOffsetY();
    }

    @Override
    public float getBaseline() {
        return baseline + field.getOffsetY();
    }

    @Override
    public boolean isValid() {
        float offsetY = field.getOffsetY();

        return !failed && field.getLayout() == layout && field.getText().toString().equals(text)
                && field.getWidth() == width && field.getHeight() == height
                && field.getCurrentTextColor() == color && field.getBaseline() == fieldBaseline
                && field.getCompoundPaddingLeft() == leftPadding
                && ink.bottom + offsetY <= field.getHeight() - field.getPaddingBottom()
                && field.getTextSize() == textSize && field.getTypeface() == typeface
                && field.getSelectionStart() == selectionStart && field.getSelectionEnd() == selectionEnd
                && BaseInputConnection.getComposingSpanStart(field.getText()) == composingStart
                && BaseInputConnection.getComposingSpanEnd(field.getText()) == composingEnd
                && layout.getLineForOffset(start) == line
                && layout.getLineForOffset(end - 1) == line && plainStyle(field);
    }

    boolean hasSameStateAs(ComposerNativeTextCapture other) {
        return !failed && other != null && field == other.field && layout == other.layout
                && revision == other.revision && text.equals(other.text)
                && width == other.width && height == other.height && color == other.color
                && fieldBaseline == other.fieldBaseline && leftPadding == other.leftPadding
                && textSize == other.textSize && typeface == other.typeface
                && selectionStart == other.selectionStart && selectionEnd == other.selectionEnd
                && composingStart == other.composingStart && composingEnd == other.composingEnd;
    }

    int fieldWidth() {
        return width;
    }

    int fieldHeight() {
        return height;
    }

    @Override
    public void draw(Canvas canvas, Consumer<Canvas> nativeDraw, ComposerTextPresentation presentation) {
        if (!isValid()) {
            nativeDraw.accept(canvas);
            return;
        }

        if (!prepare(presentation) || !replay(canvas, presentation)) {
            nativeDraw.accept(canvas);
            return;
        }

        int save = canvas.save();

        try {
            clipNative(canvas);
            nativeDraw.accept(canvas);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    boolean prepare(ComposerTextPresentation presentation) {
        try {
            if (presentation.alpha > 0) {
                layer.prepare(picture, ink.width(), ink.height(), presentation);
            }

            return true;
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            fail("Native text blur preparation failed", error);
            return false;
        }
    }

    boolean replay(Canvas canvas, ComposerTextPresentation presentation) {
        int save = canvas.save();
        Trace.beginSection("FGComposer.textReplay");
        Throwable replayFailure = null;

        try {
            canvas.translate(ink.left, ink.top + field.getOffsetY());
            applyTransform(canvas, presentation);

            if (presentation.alpha > 0) {
                layer.draw(canvas, picture, ink.width(), ink.height(), presentation);
            }
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            failed = true;
            replayFailure = error;
        } finally {
            try {
                canvas.restoreToCount(save);
            } finally {
                Trace.endSection();
            }
        }

        if (replayFailure != null) {
            fail("Native text replay failed", replayFailure);
            return false;
        }

        return true;
    }

    void clipNative(Canvas canvas) {
        float offsetY = field.getOffsetY();

        if (Build.VERSION.SDK_INT >= 26) {
            canvas.clipOutRect(ink.left, ink.top + offsetY, ink.right, ink.bottom + offsetY);
        } else {
            canvas.clipRect(ink.left, ink.top + offsetY, ink.right, ink.bottom + offsetY,
                    Region.Op.DIFFERENCE);
        }
    }

    int area() {
        return ink.width() * ink.height();
    }

    int paddedArea() {
        int padding = (int) ComposerTextPresentation.MAX_BLUR_RADIUS + 1;
        return (ink.width() + padding * 2) * (ink.height() + padding * 2);
    }

    private void fail(String message, Throwable error) {
        failed = true;
        layer.release();
        Log.e("FluxGramComposer", message, error);
    }

    static void applyTransform(Canvas canvas, ComposerTextPresentation presentation) {
        canvas.translate(presentation.translationX, presentation.translationY);
        canvas.translate(presentation.pivotX, presentation.pivotY);
        canvas.rotate(presentation.rotationDegrees);
        canvas.scale(presentation.scaleX, presentation.scaleY);
        canvas.translate(-presentation.pivotX, -presentation.pivotY);
    }

    @Override
    public void release() {
        layer.release();
    }
}
