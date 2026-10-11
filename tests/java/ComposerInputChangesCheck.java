package org.telegram.ui.fluxgram;

import org.telegram.messenger.plugins.api.ComposerInput;

public final class ComposerInputChangesCheck {
    public static void main(String[] args) {
        ComposerInputChanges changes = new ComposerInputChanges();

        for (int length = 1; length <= 512; length++) {
            String oldWord = repeat("a", length);
            String newWord = oldWord + "b";
            ComposerInput input = replace(changes, oldWord, newWord, true);
            require(input != null && input.insertedCount == 1 && input.removedCount == 0
                    && !input.bulk && input.composing, "long composing word stopped at " + length);
        }

        ComposerInput input = replace(changes, repeat("a", 4000), repeat("a", 4000) + "b", false);
        require(input.insertedCount == 1 && !input.bulk && !input.composing, "commit update without composing span");
        input = replace(changes, repeat("a", 120) + "b", repeat("a", 120), true);
        require(input.insertedCount == 0 && input.removedCount == 1 && !input.bulk, "long-word backspace");
        input = replace(changes, repeat("x", 80) + "cat" + repeat("z", 80),
                repeat("x", 80) + "coat" + repeat("z", 80), true);
        require(input.insertedCount == 1 && input.removedCount == 0 && !input.bulk, "middle edit");
        input = replace(changes, repeat("я", 100), repeat("я", 100) + "ю", true);
        require(input.insertedCount == 1 && !input.bulk, "Cyrillic composing");
        input = replace(changes, repeat("ش", 100), repeat("ش", 100) + "م", true);
        require(input.insertedCount == 1 && !input.bulk, "RTL composing");
        input = replace(changes, repeat("\uD83D\uDE00", 50), repeat("\uD83D\uDE00", 51), true);
        require(input.insertedCount == 2 && input.removedCount == 0 && !input.bulk, "UTF-16 counts");
        require(replace(changes, repeat("a", 80), repeat("a", 80), true) == null, "unchanged composing update animated");

        String prefix = "prefix ";
        String oldWord = repeat("a", 120);
        String oldText = prefix + oldWord + " suffix";
        String newText = prefix + oldWord + "b suffix";
        changes.beforeChange(oldText, prefix.length(), oldWord.length(), oldWord.length() + 1);
        changes.changed(newText, prefix.length(), oldWord.length(), oldWord.length() + 1);
        input = changes.take(newText.length(), true);
        require(input.insertedCount == 1 && !input.bulk, "nonzero replacement offset");

        input = replace(changes, "", repeat("a", 100), false);
        require(input.insertedCount == 65 && input.bulk, "large paste lost its limit");
        input = replace(changes, repeat("a", 100), repeat("b", 100), true);
        require(input.insertedCount == 65 && input.removedCount == 65 && input.bulk, "large replacement bypassed limit");
        input = replace(changes, repeat("a", 100), "", false);
        require(input.removedCount == 65 && input.bulk, "large deletion bypassed limit");

        changes.beforeChange(oldWord, 0, oldWord.length(), oldWord.length() + 1);
        changes.reset();
        changes.changed(oldWord + "b", 0, oldWord.length(), oldWord.length() + 1);
        require(changes.take(oldWord.length() + 1, true).bulk, "reset reused old snapshot");
        require(changes.take(1, false) == null, "pending change delivered twice");

        CharSequence huge = new CharSequence() {
            @Override
            public int length() {
                return 10000;
            }

            @Override
            public char charAt(int index) {
                throw new AssertionError("oversized input scanned");
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                throw new AssertionError("oversized input copied");
            }
        };
        changes.beforeChange(huge, 0, 10000, 10000);
        changes.changed(huge, 0, 10000, 10000);
        require(changes.take(huge.length(), true).bulk, "oversized field limit lost");
    }

    private static ComposerInput replace(ComposerInputChanges changes, String oldText, String newText, boolean composing) {
        changes.beforeChange(oldText, 0, oldText.length(), newText.length());
        changes.changed(newText, 0, oldText.length(), newText.length());
        return changes.take(newText.length(), composing);
    }

    private static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < count; i++) {
            text.append(value);
        }

        return text.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
