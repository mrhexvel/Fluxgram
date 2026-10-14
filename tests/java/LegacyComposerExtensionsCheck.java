package org.telegram.messenger.plugins;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LegacyComposerExtensionsCheck {
    private interface CheckedAction {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);

        AutoCloseable generic = LegacyComposerExtensions.register(
                "plugin.generic", 1, active::get, surface -> null);
        AutoCloseable text = LegacyComposerExtensions.registerText(
                "plugin.text", 1, active::get, surface -> null);
        AutoCloseable cursor = LegacyComposerExtensions.registerCursor(
                "plugin.cursor", 1, active::get, surface -> null);
        AutoCloseable selection = LegacyComposerExtensions.registerSelection(
                "plugin.selection", 1, active::get, surface -> null);
        AutoCloseable content = LegacyComposerExtensions.registerContent(
                "plugin.content", 1, active::get, surface -> null);

        rejects(() -> LegacyComposerExtensions.registerText(
                "plugin.text.conflict", 1, active::get, surface -> null));
        rejects(() -> LegacyComposerExtensions.registerCursor(
                "plugin.cursor.conflict", 1, active::get, surface -> null));
        rejects(() -> LegacyComposerExtensions.registerSelection(
                "plugin.selection.conflict", 1, active::get, surface -> null));
        rejects(() -> LegacyComposerExtensions.registerContent(
                "plugin.content.conflict", 1, active::get, surface -> null));

        content.close();
        selection.close();
        cursor.close();
        text.close();
        generic.close();

        AutoCloseable replacement = LegacyComposerExtensions.registerText(
                "plugin.text.replacement", 2, active::get, surface -> null);
        replacement.close();

        active.set(false);
        rejects(() -> LegacyComposerExtensions.registerText(
                "plugin.text.inactive", 3, active::get, surface -> null));
        active.set(true);
        LegacyComposerExtensions.registerText(
                "plugin.text.after_rollback", 4, active::get, surface -> null).close();
    }

    private static void rejects(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }

        throw new AssertionError("Expected registration rejection");
    }
}
