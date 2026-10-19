package org.telegram.messenger.plugins;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.fluxgram.JavaSettingsBlock;
import org.fluxgram.JavaSettingsBlockContext;
import org.telegram.ui.ActionBar.Theme;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class PluginSettingsBlocksCheck {
    public static void main(String[] args) throws Exception {
        AtomicBoolean generation = new AtomicBoolean(true);
        AtomicInteger actions = new AtomicInteger();
        PluginSettingsBlocks blocks = new PluginSettingsBlocks(
                "example.blocks",
                7,
                generation::get,
                (value, active, action) -> {
                    check(value == 7, "generation mismatch");

                    if (!active.getAsBoolean()) {
                        return false;
                    }

                    action.run();
                    return true;
                });
        AtomicInteger created = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        JavaSettingsBlockContext[] captured = new JavaSettingsBlockContext[2];
        AutoCloseable owner = blocks.register("rules", "Rules", context -> {
            captured[created.getAndIncrement()] = context;
            View view = new View();

            return new JavaSettingsBlock() {
                @Override
                public View getView() {
                    return view;
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        });

        check(ExternalPlugins.changes == 1, "registration did not invalidate UI");
        check(blocks.snapshot().size() == 1, "registration missing");
        PluginSettingsBlocks.Registration registration = blocks.snapshot().get(0);
        check(registration.getId().equals("rules"), "wrong ID");
        check(registration.getLabel().equals("Rules"), "wrong label");

        AtomicBoolean firstScreen = new AtomicBoolean(true);
        PluginSettingsBlocks.Registration.Binding first = registration.bind(
                new Context(), new Theme.ResourcesProvider() {}, firstScreen::get);
        PluginSettingsBlocks.Registration.Binding second = registration.bind(
                new Context(), new Theme.ResourcesProvider() {}, () -> true);
        check(created.get() == 2, "blocks are not screen scoped");
        check(first.isActive() && second.isActive(), "binding not active");
        check(captured[1].getColor(JavaSettingsBlockContext.COLOR_ACCENT) == 113,
                "theme color mismatch");
        check(captured[1].postAction(actions::incrementAndGet), "action rejected");
        check(actions.get() == 1, "action not delivered");

        ViewGroup parent = new ViewGroup();
        parent.addView(first.getView());
        firstScreen.set(false);
        check(!first.isActive(), "screen invalidation ignored");
        check(!captured[0].postAction(actions::incrementAndGet), "stale action accepted");
        first.close();
        check(first.getView().getParent() == null, "view not detached");
        check(closed.get() == 1, "screen cleanup missing");

        owner.close();
        check(blocks.snapshot().isEmpty(), "registration remained published");
        check(closed.get() == 2, "registration did not clean remaining screen");
        check(ExternalPlugins.changes == 2, "removal did not invalidate UI");

        AutoCloseable duplicate = blocks.register("same", "", context -> simpleBlock());

        try {
            blocks.register("same", "", context -> simpleBlock());
            throw new AssertionError("duplicate registration accepted");
        } catch (IllegalArgumentException expected) {
        }

        duplicate.close();

        AtomicInteger attempts = new AtomicInteger();
        AutoCloseable retry = blocks.register("retry", "", context -> {
            View view = new View();

            return new JavaSettingsBlock() {
                @Override
                public View getView() {
                    return view;
                }

                @Override
                public void close() throws Exception {
                    if (attempts.incrementAndGet() == 1) {
                        throw new Exception("retry");
                    }
                }
            };
        });
        PluginSettingsBlocks.Registration retryRegistration = blocks.snapshot().get(0);
        retryRegistration.bind(new Context(), new Theme.ResourcesProvider() {}, () -> true);

        try {
            retry.close();
            throw new AssertionError("cleanup failure hidden");
        } catch (Exception expected) {
        }

        check(blocks.snapshot().isEmpty(), "closing registration remained published");

        try {
            blocks.register("retry", "", context -> simpleBlock());
            throw new AssertionError("ID released before cleanup completed");
        } catch (IllegalArgumentException expected) {
        }

        retry.close();
        check(attempts.get() == 2, "failed cleanup not retried");

        AtomicInteger invalidAttempts = new AtomicInteger();
        blocks.register("invalid", "", context -> new JavaSettingsBlock() {
            @Override
            public View getView() {
                return null;
            }

            @Override
            public void close() throws Exception {
                if (invalidAttempts.incrementAndGet() == 1) {
                    throw new Exception("retry invalid block cleanup");
                }
            }
        });
        PluginSettingsBlocks.Registration invalidRegistration = blocks.snapshot().get(0);

        try {
            invalidRegistration.bind(new Context(), new Theme.ResourcesProvider() {}, () -> true);
            throw new AssertionError("invalid view accepted");
        } catch (IllegalStateException expected) {
            check(expected.getSuppressed().length == 1, "cleanup failure not preserved");
        }

        check(invalidAttempts.get() == 1, "invalid block cleanup not attempted");
        invalidRegistration.close();
        check(invalidAttempts.get() == 2, "invalid block cleanup not retried");
        generation.set(false);

        try {
            blocks.register("inactive", "", context -> simpleBlock());
            throw new AssertionError("inactive generation accepted registration");
        } catch (IllegalStateException expected) {
        }
    }

    private static JavaSettingsBlock simpleBlock() {
        View view = new View();

        return new JavaSettingsBlock() {
            @Override
            public View getView() {
                return view;
            }

            @Override
            public void close() {
            }
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
