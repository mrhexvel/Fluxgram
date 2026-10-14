package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.view.View;

import org.telegram.messenger.FileLog;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ComposerInputRendererHostSurfaceCheck {
    public static void main(String[] args) throws Exception {
        checkNativeFastPathWithoutRenderer();
        checkCapturedNativeFallbackRunsOnce();
        checkCommitExpiryConflictAndCleanup();
        checkPluginFailureFallsBackAndQuarantines();
    }

    private static void checkNativeFastPathWithoutRenderer() throws Exception {
        Picture.reset();
        View view = new View(320, 64);
        ComposerInputRendererHostSurface surface =
                ComposerInputRendererHostSurface.attach(view);
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
                ComposerInputRendererHostSurface.attach(view);
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.fallback", 1, () -> true,
                () -> new ComposerInputRendererExtensions.Renderer() {
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
                ComposerInputRendererHostSurface.attach(view);
        AtomicReference<ComposerInputRendererExtensions.Frame> retained = new AtomicReference<>();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.commit", 1, () -> true,
                () -> frame -> {
                    retained.set(frame);
                    return ComposerInputRendererExtensions.Result.COMMIT;
                });

        rejectsRuntime(() -> ComposerInputRendererExtensions.register(
                "plugin.conflict", 1, () -> true,
                () -> frame -> ComposerInputRendererExtensions.Result.COMMIT));
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
                ComposerInputRendererHostSurface.attach(view);
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.failure", 1, () -> true,
                () -> new ComposerInputRendererExtensions.Renderer() {
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
