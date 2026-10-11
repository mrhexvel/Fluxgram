package org.telegram.ui.fluxgram;

import org.telegram.messenger.plugins.api.ComposerInput;

import java.util.Arrays;

final class ComposerInputChanges {
    static final int MAX_TEXT_LENGTH = 4096;
    private char[] previous;
    private int captured;
    private int start;
    private int expectedAdded;
    private int previousLength;
    private int inserted;
    private int removed;

    void beforeChange(CharSequence text, int offset, int count, int after) {
        clearSnapshot();

        if (text == null || text.length() > MAX_TEXT_LENGTH || count <= 0 || after <= 0
                || count > MAX_TEXT_LENGTH || after > MAX_TEXT_LENGTH
                || offset < 0 || offset > text.length() || count > text.length() - offset) {
            return;
        }

        if (previous == null || previous.length < count) {
            int capacity = previous == null ? 64 : previous.length;

            while (capacity < count) {
                capacity *= 2;
            }

            previous = new char[capacity];
        }

        for (int i = 0; i < count; i++) {
            previous[i] = text.charAt(offset + i);
        }

        captured = count;
        start = offset;
        expectedAdded = after;
        previousLength = text.length();
    }

    void changed(CharSequence text, int offset, int before, int count) {
        int added = Math.max(0, count);
        int deleted = Math.max(0, before);

        if (captured > 0 && offset == start && before == captured && count == expectedAdded
                && text != null && text.length() <= MAX_TEXT_LENGTH
                && text.length() == previousLength - before + count
                && offset >= 0 && offset <= text.length() && count <= text.length() - offset) {
            int prefix = 0;
            int limit = Math.min(before, count);

            while (prefix < limit && previous[prefix] == text.charAt(offset + prefix)) {
                prefix++;
            }

            int suffix = 0;

            while (suffix < limit - prefix
                    && previous[before - 1 - suffix] == text.charAt(offset + count - 1 - suffix)) {
                suffix++;
            }

            added -= prefix + suffix;
            deleted -= prefix + suffix;
        }

        inserted = Math.min(65, added);
        removed = Math.min(65, deleted);
        clearSnapshot();
    }

    ComposerInput take(int length, boolean composing) {
        ComposerInput input = inserted + removed == 0 ? null : new ComposerInput(
                inserted, removed, length, inserted + removed > 64 || length > MAX_TEXT_LENGTH, composing);
        reset();
        return input;
    }

    void reset() {
        inserted = 0;
        removed = 0;
        clearSnapshot();
    }

    private void clearSnapshot() {
        if (captured > 0) {
            Arrays.fill(previous, 0, captured, '\0');
            captured = 0;
        }
    }
}
