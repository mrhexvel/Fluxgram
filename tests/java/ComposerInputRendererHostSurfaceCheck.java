package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.ComposerExpandedBounds;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ComposerInputRendererHostSurfaceCheck {
    public static void main(String[] args) throws Exception {
        checkNativeFastPathWithoutRenderer();
        checkCapturedNativeFallbackRunsOnce();
        checkCommitExpiryConflictAndCleanup();
        checkPluginFailureFallsBackAndQuarantines();
        checkManagedLifecycleCallbacks();
    }

    private static void checkNativeFastPathWithoutRenderer() throws Exception {
        Picture.reset();
        View view = new View(320, 64);
        ComposerInputRendererHostSurface surface =
                attach(view);
        AtomicInteger nativeCalls = new AtomicInteger();

        surface.draw(new Canvas(), canvas -> nativeCalls.incrementAndGet());
        require(nativeCalls.get() == 1, "native fast path did not draw once");
        require(Picture.beginCount == 0, "native fast path recorded a picture");
        surface.close();
    }

    private static void checkCapturedNativeFallbackRunsOnce() throws Exception {
        Picture.reset();
        View view = new View(320, 64);
        ComposerInputRendererHostSurface surface =
                attach(view);
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.fallback", 1, () -> true,
                managed -> new ComposerInputRendererExtensions.Renderer() {
                    @Override
                    public ComposerInputRendererExtensions.Result draw(
                            ComposerInputRendererExtensions.Frame frame) {
                        frame.drawNative();
                        return ComposerInputRendererExtensions.Result.FALLBACK;
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                });

        surface.draw(new Canvas(), canvas -> nativeCalls.incrementAndGet());
        require(nativeCalls.get() == 1, "fallback repeated native continuation");
        require(Picture.beginCount == 2, "replacement did not stage both pictures");
        require(Picture.drawCount == 2, "native fallback did not replay captured draw");
        registration.close();
        surface.close();
        require(closes.get() == 1, "fallback renderer cleanup mismatch");
    }

    private static void checkCommitExpiryConflictAndCleanup() throws Exception {
        Picture.reset();
        View view = new View(320, 64);
        ComposerInputRendererHostSurface surface =
                attach(view);
        AtomicReference<ComposerInputRendererExtensions.Frame> retained = new AtomicReference<>();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.commit", 1, () -> true,
                managed -> frame -> {
                    retained.set(frame);
                    return ComposerInputRendererExtensions.Result.COMMIT;
                });

        rejectsRuntime(() -> ComposerInputRendererExtensions.register(
                "plugin.conflict", 1, () -> true,
                managed -> frame -> ComposerInputRendererExtensions.Result.COMMIT));
        surface.draw(new Canvas(), canvas -> {
            throw new AssertionError("committed renderer used native fallback");
        });
        require(Picture.beginCount == 1 && Picture.drawCount == 1,
                "committed replacement replay mismatch");
        rejectsRuntime(() -> retained.get().getWidth());
        registration.close();
        surface.close();
    }

    private static void checkPluginFailureFallsBackAndQuarantines() throws Exception {
        Picture.reset();
        FileLog.errors = 0;
        HostExtensionAttribution.handled = 0;
        View view = new View(320, 64);
        ComposerInputRendererHostSurface surface =
                attach(view);
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.failure", 1, () -> true,
                managed -> new ComposerInputRendererExtensions.Renderer() {
                    @Override
                    public ComposerInputRendererExtensions.Result draw(
                            ComposerInputRendererExtensions.Frame frame) {
                        throw new IllegalStateException("intentional renderer failure");
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                });

        surface.draw(new Canvas(), canvas -> nativeCalls.incrementAndGet());
        require(nativeCalls.get() == 1, "plugin failure did not use native fallback");
        require(closes.get() == 1 && HostExtensionAttribution.handled == 1
                        && FileLog.errors == 1,
                "plugin failure quarantine mismatch");
        Picture.reset();
        surface.draw(new Canvas(), canvas -> nativeCalls.incrementAndGet());
        require(nativeCalls.get() == 2 && Picture.beginCount == 0,
                "quarantined renderer remained on draw path");
        registration.close();
        surface.close();
    }

    private static void checkManagedLifecycleCallbacks() throws Exception {
        View view = new View(320, 64);
        ComposerInputRendererHostSurface surface = attach(view);
        AtomicReference<ComposerInputRendererExtensions.Surface> managed = new AtomicReference<>();
        AtomicReference<ComposerInput> input = new AtomicReference<>();
        AtomicReference<ComposerTextLayoutChange> layout = new AtomicReference<>();
        AtomicReference<ComposerInputRendererExtensions.ExpandedFrame> expanded = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger frames = new AtomicInteger();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.lifecycle", 1, () -> true,
                value -> {
                    managed.set(value);

                    return new ComposerInputRendererExtensions.Renderer() {
                        @Override
                        public int getCapabilities() {
                            return ComposerInputRendererExtensions.CAPABILITY_INPUT
                                    | ComposerInputRendererExtensions.CAPABILITY_TEXT_LAYOUT
                                    | ComposerInputRendererExtensions.CAPABILITY_SEND
                                    | ComposerInputRendererExtensions.CAPABILITY_EXPANDED_DRAW;
                        }

                        @Override
                        public void onInput(ComposerInput value) {
                            input.set(value);
                        }

                        @Override
                        public void onTextLayoutChanged(ComposerTextLayoutChange value) {
                            layout.set(value);
                        }

                        @Override
                        public void onSend(org.telegram.messenger.plugins.api.ComposerSendEvent event) {
                            sends.incrementAndGet();
                        }

                        @Override
                        public boolean onFrame(long frameTimeNanos) {
                            return frames.incrementAndGet() == 1;
                        }

                        @Override
                        public ComposerInputRendererExtensions.Result draw(
                                ComposerInputRendererExtensions.Frame frame) {
                            frame.drawNative();
                            return ComposerInputRendererExtensions.Result.COMMIT;
                        }

                        @Override
                        public void drawExpanded(ComposerInputRendererExtensions.ExpandedFrame frame) {
                            expanded.set(frame);
                        }
                    };
                });
        ComposerInput event = new ComposerInput(1, 0, 1, false, true);
        ComposerTextLayoutChange change = new ComposerTextLayoutChange(
                2, null, null, 0, 0, 1, false);
        surface.input(event);
        surface.textLayoutChanged(change);
        surface.sent(true, 20, 30);
        managed.get().requestFrame();
        surface.draw(new Canvas(), canvas -> {});
        surface.draw(new Canvas(), canvas -> {});
        surface.drawExpanded(new Canvas(), new ComposerExpandedBounds(-10, -10, 330, 74));

        require(managed.get().getAccount() == 1 && managed.get().getOwnerId() == 2
                        && managed.get().getDialogId() == 3,
                "managed surface identity mismatch");
        require(input.get() == event && layout.get() == change && sends.get() == 1,
                "managed lifecycle event mismatch");
        require(frames.get() == 2, "managed frame demand mismatch");
        require(expanded.get() != null, "managed expanded draw missing");
        rejectsRuntime(() -> expanded.get().getCanvas());
        registration.close();
        surface.close();
        require(!managed.get().isActive(), "closed managed surface remained active");
    }

    private static ComposerInputRendererHostSurface attach(View view) {
        ComposerGeometry geometry = new ComposerGeometry(320, 64, 1, 0xFF0088FF, false);
        return ComposerInputRendererHostSurface.attach(
                view, 1, 2, 3, () -> geometry, () -> true, view::invalidate);
    }

    private static void rejectsRuntime(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }

        throw new AssertionError("Expected runtime rejection");
    }

    private static void require(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
