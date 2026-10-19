package org.telegram.messenger.fluxgram;

final class ZalgoDisplayFilterPolicy {
    private static final int MAX_COMBINING_MARKS = 3;
    private static final char HIDDEN_MARK = '\u2060';

    static String filter(String source, boolean[] protectedCodeUnits) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        if (protectedCodeUnits != null && protectedCodeUnits.length != source.length()) {
            throw new IllegalArgumentException("protectedCodeUnits length must match source length");
        }

        char[] result = null;
        int combiningMarks = 0;

        for (int offset = 0; offset < source.length();) {
            int codePoint = source.codePointAt(offset);
            int charCount = Character.charCount(codePoint);

            if (isPreservedEmojiMark(codePoint)) {
                offset += charCount;
                continue;
            }

            int type = Character.getType(codePoint);
            boolean combiningMark = type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            if (!combiningMark) {
                combiningMarks = 0;
                offset += charCount;
                continue;
            }

            combiningMarks++;
            if (combiningMarks > MAX_COMBINING_MARKS
                    && !isProtected(protectedCodeUnits, offset, charCount)) {
                if (result == null) {
                    result = source.toCharArray();
                }
                for (int index = offset; index < offset + charCount; index++) {
                    result[index] = HIDDEN_MARK;
                }
            }

            offset += charCount;
        }

        return result == null ? source : new String(result);
    }

    private static boolean isPreservedEmojiMark(int codePoint) {
        return codePoint == 0x20E3
                || codePoint >= 0xFE00 && codePoint <= 0xFE0F
                || codePoint >= 0xE0100 && codePoint <= 0xE01EF;
    }

    private static boolean isProtected(boolean[] protectedCodeUnits, int offset, int charCount) {
        if (protectedCodeUnits == null) {
            return false;
        }
        for (int index = offset; index < offset + charCount; index++) {
            if (protectedCodeUnits[index]) {
                return true;
            }
        }
        return false;
    }

    private ZalgoDisplayFilterPolicy() {
    }
}
