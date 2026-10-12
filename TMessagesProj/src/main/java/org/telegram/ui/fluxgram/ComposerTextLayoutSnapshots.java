package org.telegram.ui.fluxgram;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;
import android.text.style.ParagraphStyle;
import android.text.style.ReplacementSpan;
import android.text.style.SuggestionSpan;
import android.text.style.UnderlineSpan;
import android.view.inputmethod.BaseInputConnection;

import org.telegram.messenger.plugins.api.ComposerTextClusterSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextFontSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLineSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextRunSnapshot;
import org.telegram.ui.Components.EditTextCaption;

import java.util.ArrayList;

public final class ComposerTextLayoutSnapshots {
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_VISIBLE_LINES = 16;
    private static final int MAX_CLUSTERS = 256;

    private ComposerTextLayoutSnapshots() {
    }

    public static ComposerTextLayoutSnapshot capture(
            EditTextCaption field, long revision, String expectedText) {
        Layout layout = field.getLayout();
        Editable content = field.getText();

        if (layout == null || content == null || expectedText == null
                || content.length() > MAX_TEXT_LENGTH || !TextUtils.equals(content, expectedText)
                || field.getWidth() <= 0 || field.getHeight() <= 0 || layout.getLineCount() <= 0) {
            return null;
        }

        float originX = field.getCompoundPaddingLeft() - field.getScrollX();
        float originY = field.getBaseline() - layout.getLineBaseline(0) - field.getScrollY();
        int viewportTop = Math.max(0, (int) Math.floor(field.getTotalPaddingTop() - originY));
        int viewportBottom = Math.min(layout.getHeight(),
                (int) Math.ceil(field.getHeight() - field.getPaddingBottom() - originY));

        if (viewportBottom < viewportTop) {
            return null;
        }

        int firstLine = layout.getLineForVertical(viewportTop);
        int lastLine = layout.getLineForVertical(viewportBottom);
        int endLine = Math.min(lastLine, firstLine + MAX_VISIBLE_LINES - 1);
        ArrayList<ComposerTextLineSnapshot> lines = new ArrayList<>(endLine - firstLine + 1);

        for (int line = firstLine; line <= endLine; line++) {
            int start = layout.getLineStart(line);
            int end = layout.getLineEnd(line);
            int visibleEnd = layout.getLineVisibleEnd(line);

            if (start < 0 || start > end || end > content.length()
                    || visibleEnd < start || visibleEnd > end) {
                return null;
            }

            lines.add(new ComposerTextLineSnapshot(
                    start,
                    end,
                    visibleEnd,
                    originX + layout.getLineLeft(line),
                    originX + layout.getLineRight(line),
                    originY + layout.getLineTop(line),
                    originY + layout.getLineBottom(line),
                    originY + layout.getLineBaseline(line),
                    layout.getParagraphDirection(line) == Layout.DIR_RIGHT_TO_LEFT));
        }

        int spanFlags = spanFlags(content);
        boolean unsupportedSpans = (spanFlags & (ComposerTextLayoutSnapshot.SPAN_METRIC
                | ComposerTextLayoutSnapshot.SPAN_REPLACEMENT
                | ComposerTextLayoutSnapshot.SPAN_PARAGRAPH
                | ComposerTextLayoutSnapshot.SPAN_UNKNOWN_CHARACTER)) != 0;
        boolean unsupportedClusters = unsupportedSpans;
        boolean truncated = firstLine > 0 || endLine < layout.getLineCount() - 1;
        ArrayList<ComposerTextClusterSnapshot> clusters = new ArrayList<>();
        ArrayList<ComposerTextRunSnapshot> runs = new ArrayList<>();

        if (!unsupportedSpans) {
            clusterLines:
            for (int line = firstLine; line <= endLine; line++) {
                int clusterLineStart = clusters.size();
                int runLineStart = runs.size();
                int runStart = layout.getLineStart(line);
                int lineEnd = layout.getLineVisibleEnd(line);

                while (runStart < lineEnd) {
                    boolean rtl = layout.isRtlCharAt(runStart);
                    int runEnd = runStart + 1;

                    while (runEnd < lineEnd && layout.isRtlCharAt(runEnd) == rtl) {
                        runEnd++;
                    }

                    int offset = runStart;
                    int clusterRunStart = clusters.size();

                    while (offset < runEnd) {
                        if (clusters.size() >= MAX_CLUSTERS) {
                            truncated = true;

                            while (clusters.size() > clusterRunStart) {
                                clusters.remove(clusters.size() - 1);
                            }

                            break clusterLines;
                        }

                        int next = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            next = field.getPaint().getTextRunCursor(
                                    content, runStart, runEnd, rtl, offset, Paint.CURSOR_AFTER);
                        }

                        if (next <= offset || next > runEnd || unsafeBoundary(content, next, runStart, runEnd)) {
                            unsupportedClusters = true;

                            while (clusters.size() > clusterLineStart) {
                                clusters.remove(clusters.size() - 1);
                            }

                            while (runs.size() > runLineStart) {
                                runs.remove(runs.size() - 1);
                            }

                            continue clusterLines;
                        }

                        clusters.add(new ComposerTextClusterSnapshot(
                                offset,
                                next,
                                line - firstLine,
                                rtl,
                                originX + layout.getPrimaryHorizontal(offset),
                                originX + layout.getPrimaryHorizontal(next),
                                originX + layout.getSecondaryHorizontal(offset),
                                originX + layout.getSecondaryHorizontal(next),
                                Math.abs(field.getPaint().getRunAdvance(
                                        content, offset, next, runStart, runEnd, rtl, next))));
                        offset = next;
                    }

                    runs.add(new ComposerTextRunSnapshot(
                            runStart,
                            runEnd,
                            line - firstLine,
                            rtl,
                            clusterRunStart,
                            clusters.size() - clusterRunStart));

                    runStart = runEnd;
                }
            }
        }

        return new ComposerTextLayoutSnapshot(
                revision,
                expectedText,
                field.getSelectionStart(),
                field.getSelectionEnd(),
                BaseInputConnection.getComposingSpanStart(content),
                BaseInputConnection.getComposingSpanEnd(content),
                field.getWidth(),
                field.getHeight(),
                originX,
                originY,
                field.getScrollX(),
                field.getScrollY(),
                truncated,
                unsupportedSpans,
                unsupportedClusters,
                spanFlags,
                font(field.getPaint(), field.getCurrentTextColor()),
                lines.toArray(new ComposerTextLineSnapshot[0]),
                clusters.toArray(new ComposerTextClusterSnapshot[0]),
                runs.toArray(new ComposerTextRunSnapshot[0]));
    }

    private static int spanFlags(CharSequence content) {
        if (!(content instanceof Spanned)) {
            return 0;
        }

        Spanned spans = (Spanned) content;
        int flags = 0;

        if (spans.getSpans(0, content.length(), ParagraphStyle.class).length != 0) {
            flags |= ComposerTextLayoutSnapshot.SPAN_PARAGRAPH;
        }

        for (CharacterStyle style : spans.getSpans(0, content.length(), CharacterStyle.class)) {
            if (style instanceof ReplacementSpan) {
                flags |= ComposerTextLayoutSnapshot.SPAN_REPLACEMENT;
            } else if (style instanceof MetricAffectingSpan) {
                flags |= ComposerTextLayoutSnapshot.SPAN_METRIC;
            } else if (style instanceof UnderlineSpan) {
                flags |= ComposerTextLayoutSnapshot.SPAN_UNDERLINE;
            } else if (style instanceof SuggestionSpan) {
                flags |= ComposerTextLayoutSnapshot.SPAN_SUGGESTION;
            } else {
                flags |= ComposerTextLayoutSnapshot.SPAN_UNKNOWN_CHARACTER;
            }
        }

        return flags;
    }

    private static ComposerTextFontSnapshot font(TextPaint paint, int color) {
        Typeface typeface = paint.getTypeface();
        int style = typeface == null ? Typeface.NORMAL : typeface.getStyle();
        int weight = (style & Typeface.BOLD) != 0 ? 700 : 400;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && typeface != null) {
            weight = typeface.getWeight();
        }

        return new ComposerTextFontSnapshot(
                paint.getTextSize(),
                paint.getTextScaleX(),
                paint.getTextSkewX(),
                paint.getLetterSpacing(),
                paint.getFlags(),
                color,
                style,
                weight,
                paint.getTextLocales().toLanguageTags(),
                paint.getFontFeatureSettings(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? paint.getFontVariationSettings() : null);
    }

    private static boolean unsafeBoundary(CharSequence text, int offset, int start, int end) {
        if (offset <= start || offset >= end) {
            return false;
        }

        if (Character.isHighSurrogate(text.charAt(offset - 1))
                && Character.isLowSurrogate(text.charAt(offset))) {
            return true;
        }

        int before = Character.codePointBefore(text, offset);
        int after = Character.codePointAt(text, offset);

        if (before == 0x200D || after == 0x200D || before == '\r' && after == '\n'
                || isClusterExtension(after)) {
            return true;
        }

        if (!isRegionalIndicator(before) || !isRegionalIndicator(after)) {
            return false;
        }

        int regionalCount = 0;
        int index = offset;

        while (index > start) {
            int codePoint = Character.codePointBefore(text, index);

            if (!isRegionalIndicator(codePoint)) {
                break;
            }

            regionalCount++;
            index -= Character.charCount(codePoint);
        }

        return regionalCount % 2 != 0;
    }

    private static boolean isClusterExtension(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || codePoint >= 0xFE00 && codePoint <= 0xFE0F
                || codePoint >= 0xE0100 && codePoint <= 0xE01EF
                || codePoint >= 0x1F3FB && codePoint <= 0x1F3FF
                || codePoint >= 0xE0020 && codePoint <= 0xE007F;
    }

    private static boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }
}
