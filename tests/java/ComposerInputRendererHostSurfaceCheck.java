package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.ComposerExpandedBounds;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextCapture;
import org.telegram.messenger.plugins.api.ComposerTextClusterCapture;
import org.telegram.messenger.plugins.api.ComposerTextClusterPresentation;
import org.telegram.messenger.plugins.api.ComposerTextClusterSelection;
import org.telegram.messenger.plugins.api.ComposerTextClusterSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;
import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLineSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ComposerInputRendererHostSurfaceCheck {
    public static void main(String[] args) throws Exception {
        checkNativeFastPathWithoutRenderer();
        checkCapturedNativeFallbackRunsOnce();
        checkScrolledNativeContinuation();
        checkCommitExpiryConflictAndCleanup();
        checkPluginFailureFallsBackAndQuarantines();
        checkManagedLifecycleCallbacks();
        checkManagedTextCaptures();
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
        view.scrollTo(5, 17);
        ComposerInputRendererHostSurface surface =
                attach(view);
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicReference<Float> nativeX = new AtomicReference<>();
        AtomicReference<Float> nativeY = new AtomicReference<>();
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

        Canvas target = new Canvas();
        target.translate(-5, -17);
        surface.draw(target, canvas -> {
            nativeCalls.incrementAndGet();
            nativeX.set(canvas.getTranslationX());
            nativeY.set(canvas.getTranslationY());
        });
        require(nativeCalls.get() == 1, "fallback repeated native continuation");
        require(nativeX.get() == -5 && nativeY.get() == -17,
                "fallback native continuation lost view scroll");
        require(Picture.beginCount == 2, "replacement did not stage both pictures");
        require(Picture.drawCount == 2, "native fallback did not replay captured draw");
        require(Picture.drawTranslationX[1] == 0 && Picture.drawTranslationY[1] == 0,
                "captured native fallback applied view scroll twice");
        require(target.getTranslationX() == -5 && target.getTranslationY() == -17,
                "captured native fallback changed source canvas transform");
        registration.close();
        surface.close();
        require(closes.get() == 1, "fallback renderer cleanup mismatch");
    }

    private static void checkScrolledNativeContinuation() throws Exception {
        Picture.reset();
        View view = new View(320, 64);
        view.scrollTo(7, 29);
        ComposerInputRendererHostSurface surface = attach(view);
        AtomicReference<Float> nativeX = new AtomicReference<>();
        AtomicReference<Float> nativeY = new AtomicReference<>();
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.scroll", 1, () -> true,
                managed -> frame -> {
                    frame.drawNative();
                    return ComposerInputRendererExtensions.Result.COMMIT;
                });
        Canvas target = new Canvas();
        target.translate(-7, -29);

        surface.draw(target, canvas -> {
            nativeX.set(canvas.getTranslationX());
            nativeY.set(canvas.getTranslationY());
        });

        require(nativeX.get() == -7 && nativeY.get() == -29,
                "native continuation lost view scroll");
        require(Picture.drawCount == 2
                        && Picture.drawTranslationX[0] == 0
                        && Picture.drawTranslationY[0] == 0
                        && Picture.drawTranslationX[1] == 0
                        && Picture.drawTranslationY[1] == 0,
                "replacement replay applied view scroll twice");
        require(target.getTranslationX() == -7 && target.getTranslationY() == -29,
                "replacement replay changed source canvas transform");
        registration.close();
        surface.close();
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
        surface.textLayoutChanged(change, null);
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

    private static void checkManagedTextCaptures() throws Exception {
        View view = new View(320, 64);
        FakeTextCapture glyph = new FakeTextCapture(7, 0, 1);
        FakeRemovedCapture removed = new FakeRemovedCapture(7, 0, 1);
        FakeClusterCapture current = new FakeClusterCapture(7, glyph);
        AtomicReference<String> capturedText = new AtomicReference<>();
        ComposerInputRendererHostSurface surface = ComposerInputRendererHostSurface.attach(
                view, 1, 2, 3, () -> new ComposerGeometry(320, 64, 1, 0xFF0088FF, false),
                () -> true, view::invalidate, (selection, text) -> {
                    capturedText.set(text);
                    return current;
                });
        AutoCloseable registration = ComposerInputRendererExtensions.register(
                "plugin.capture", 1, () -> true,
                managed -> new ComposerInputRendererExtensions.Renderer() {
                    @Override
                    public int getCapabilities() {
                        return ComposerInputRendererExtensions.CAPABILITY_TEXT_LAYOUT
                                | ComposerInputRendererExtensions.CAPABILITY_TEXT_CAPTURE;
                    }

                    @Override
                    public void onTextLayoutChanged(ComposerTextLayoutChange change) {
                        managed.selectTextClusters(new ComposerTextClusterSelection(
                                change.revision,
                                new ComposerTextCandidate[] {
                                        new ComposerTextCandidate(change.revision, 0, 1)
                                }));
                    }

                    @Override
                    public ComposerInputRendererExtensions.Result draw(
                            ComposerInputRendererExtensions.Frame frame) {
                        ComposerTextClusterCapture capture = frame.getTextClusterCapture();
                        require(capture != null && frame.getRemovedTextClusterCapture() == removed,
                                "managed text capture missing");
                        ComposerTextPresentation presentation = new ComposerTextPresentation(
                                0, 0, 1, 1, 1, 0, 5, 5);
                        frame.drawNative(
                                new ComposerTextClusterPresentation(
                                        capture.getRevision(),
                                        new ComposerTextPresentation[] {presentation}),
                                new ComposerTextClusterPresentation(
                                        removed.getRevision(),
                                        new ComposerTextPresentation[] {presentation}));
                        return ComposerInputRendererExtensions.Result.COMMIT;
                    }
                });
        ComposerTextLayoutSnapshot after = new ComposerTextLayoutSnapshot(
                7, "a", 1, 1, -1, -1, 320, 64, 0, 0, 0, 0,
                false, false, false,
                new ComposerTextLineSnapshot[] {
                        new ComposerTextLineSnapshot(0, 1, 1, 0, 10, 0, 20, 15, false)
                },
                new ComposerTextClusterSnapshot[] {
                        new ComposerTextClusterSnapshot(0, 1, 0, false, 0, 10, 0, 10, 10)
                });
        surface.textLayoutChanged(new ComposerTextLayoutChange(
                7, null, after, 0, 1, 1, false), removed);
        AtomicInteger nativeCalls = new AtomicInteger();
        surface.draw(new Canvas(), canvas -> nativeCalls.incrementAndGet());

        require("a".equals(capturedText.get()), "managed text capture content mismatch");
        require(nativeCalls.get() == 1 && glyph.draws == 1 && removed.draws == 1,
                "managed text capture draw mismatch");
        current.valid = false;
        surface.draw(new Canvas(), canvas -> nativeCalls.incrementAndGet());
        require(nativeCalls.get() == 2 && glyph.draws == 1 && removed.draws == 1,
                "stale managed capture did not use direct native fallback");
        registration.close();
        require(glyph.releases == 1 && removed.releases == 1,
                "managed text capture cleanup mismatch");
        surface.close();
    }

    private static ComposerInputRendererHostSurface attach(View view) {
        ComposerGeometry geometry = new ComposerGeometry(320, 64, 1, 0xFF0088FF, false);
        return ComposerInputRendererHostSurface.attach(
                view, 1, 2, 3, () -> geometry, () -> true, view::invalidate,
                (selection, text) -> null);
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

    private static class FakeTextCapture implements ComposerTextCapture {
        final long revision;
        final int start;
        final int end;
        int draws;
        int releases;

        FakeTextCapture(long revision, int start, int end) {
            this.revision = revision;
            this.start = start;
            this.end = end;
        }

        @Override
        public long getRevision() {
            return revision;
        }

        @Override
        public int getStart() {
            return start;
        }

        @Override
        public int getEnd() {
            return end;
        }

        @Override
        public float getLeft() {
            return 20;
        }

        @Override
        public float getTop() {
            return 20;
        }

        @Override
        public float getRight() {
            return 30;
        }

        @Override
        public float getBottom() {
            return 30;
        }

        @Override
        public float getBaseline() {
            return 28;
        }
    }

    private static final class FakeClusterCapture
            implements ComposerInputRendererHostSurface.TextClusterCapture {
        private final long revision;
        private final FakeTextCapture capture;
        boolean valid = true;

        FakeClusterCapture(long revision, FakeTextCapture capture) {
            this.revision = revision;
            this.capture = capture;
        }

        @Override
        public long getRevision() {
            return revision;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public ComposerTextCapture getCapture(int index) {
            return capture;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void draw(Canvas canvas, java.util.function.Consumer<Canvas> nativeDraw,
                ComposerTextClusterPresentation presentation) {
            capture.draws++;
            nativeDraw.accept(canvas);
        }

        @Override
        public void release() {
            capture.releases++;
        }
    }

    private static final class FakeRemovedCapture
            implements ComposerInputRendererHostSurface.RemovedTextClusterCapture {
        private final long revision;
        private final FakeTextCapture capture;
        int draws;
        int releases;

        FakeRemovedCapture(long revision, int start, int end) {
            this.revision = revision;
            capture = new FakeTextCapture(revision, start, end);
        }

        @Override
        public long getRevision() {
            return revision;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public ComposerTextCapture getCapture(int index) {
            return capture;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void draw(Canvas canvas, ComposerTextClusterPresentation presentation) {
            draws++;
        }

        @Override
        public void release() {
            releases++;
        }
    }
}
