package org.telegram.ui.fluxgram;

import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.SuggestionSpan;
import android.text.style.UnderlineSpan;
import android.view.inputmethod.BaseInputConnection;

import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLineSnapshot;
import org.telegram.ui.Components.EditTextCaption;

import java.util.ArrayList;

public final class ComposerTextLayoutSnapshots {
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_VISIBLE_LINES = 16;

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
                firstLine > 0 || endLine < layout.getLineCount() - 1,
                hasUnsupportedSpans(content),
                lines.toArray(new ComposerTextLineSnapshot[0]));
    }

    private static boolean hasUnsupportedSpans(CharSequence content) {
        if (!(content instanceof Spanned)) {
            return false;
        }

        Spanned spans = (Spanned) content;

        if (spans.getSpans(0, content.length(), ParagraphStyle.class).length != 0) {
            return true;
        }

        for (CharacterStyle style : spans.getSpans(0, content.length(), CharacterStyle.class)) {
            if (!(style instanceof UnderlineSpan) && !(style instanceof SuggestionSpan)) {
                return true;
            }
        }

        return false;
    }
}
