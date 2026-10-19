package org.telegram.messenger.fluxgram;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.utils.code.highlight.CodeHighlighting;

public final class ZalgoDisplayFilter {
    public static CharSequence filter(CharSequence text) {
        if (!GeneralSettings.filtersZalgoText() || TextUtils.isEmpty(text)) {
            return text;
        }

        boolean[] protectedCodeUnits = text instanceof Spanned
                ? collectProtectedCodeUnits((Spanned) text)
                : null;
        String source = text.toString();
        String filtered = ZalgoDisplayFilterPolicy.filter(source, protectedCodeUnits);
        if (filtered == source) {
            return text;
        }
        if (!(text instanceof Spanned)) {
            return filtered;
        }

        SpannableStringBuilder result = new SpannableStringBuilder(filtered);
        TextUtils.copySpansFrom((Spanned) text, 0, text.length(), Object.class, result, 0);

        return result;
    }

    private static boolean[] collectProtectedCodeUnits(Spanned text) {
        boolean[] protectedCodeUnits = null;

        protectedCodeUnits = protectSpans(text, ReplacementSpan.class, protectedCodeUnits);
        protectedCodeUnits = protectSpans(text, CodeHighlighting.Span.class, protectedCodeUnits);
        protectedCodeUnits = protectSpans(text, URLSpanMono.class, protectedCodeUnits);

        TextStyleSpan[] styleSpans = text.getSpans(0, text.length(), TextStyleSpan.class);
        for (TextStyleSpan span : styleSpans) {
            int flags = span.getStyleFlags();
            if ((flags & (TextStyleSpan.FLAG_STYLE_MONO | TextStyleSpan.FLAG_STYLE_CODE)) != 0) {
                protectedCodeUnits = protectRange(text, span, protectedCodeUnits);
            }
        }

        return protectedCodeUnits;
    }

    private static <T> boolean[] protectSpans(Spanned text, Class<T> type, boolean[] protectedCodeUnits) {
        T[] spans = text.getSpans(0, text.length(), type);
        for (T span : spans) {
            protectedCodeUnits = protectRange(text, span, protectedCodeUnits);
        }

        return protectedCodeUnits;
    }

    private static boolean[] protectRange(Spanned text, Object span, boolean[] protectedCodeUnits) {
        int start = Math.max(0, text.getSpanStart(span));
        int end = Math.min(text.length(), text.getSpanEnd(span));
        if (start >= end) {
            return protectedCodeUnits;
        }
        if (protectedCodeUnits == null) {
            protectedCodeUnits = new boolean[text.length()];
        }
        for (int index = start; index < end; index++) {
            protectedCodeUnits[index] = true;
        }

        return protectedCodeUnits;
    }

    private ZalgoDisplayFilter() {
    }
}
