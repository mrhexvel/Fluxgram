package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.view.View;

import java.util.function.Consumer;

public final class ComposerInputRendererHostSurface implements AutoCloseable {
    private static final int MAX_DIMENSION = 2048;
    private static final int MAX_AREA = 2 * 1024 * 1024;

    private final View view;
    private final Picture picture = new Picture();
    private final Picture nativePicture = new Picture();
    private final HostExtensionRegistry.Endpoint<ComposerInputRendererExtensions.Frame, Void> endpoint;
    private boolean closed;

    private ComposerInputRendererHostSurface(View view) {
        if (view == null) {
            throw new IllegalArgumentException("Composer input view is required");
        }

        this.view = view;
        endpoint = ComposerInputRendererExtensions.openEndpoint();
        ComposerInputRendererExtensions.addSurface(this);
    }

    public static ComposerInputRendererHostSurface attach(View view) {
        return new ComposerInputRendererHostSurface(view);
    }

    public void draw(Canvas canvas, Consumer<Canvas> nativeDraw) {
        if (canvas == null || nativeDraw == null) {
            throw new IllegalArgumentException("Composer draw arguments are required");
        }

        int width = view.getWidth();
        int height = view.getHeight();

        if (closed || width <= 0 || height <= 0
                || width > MAX_DIMENSION || height > MAX_DIMENSION
                || (long) width * height > MAX_AREA || !endpoint.hasReplacement()) {
            nativeDraw.accept(canvas);
            return;
        }

        Canvas recording = null;
        Frame frame = null;
        boolean committed = false;

        try {
            recording = picture.beginRecording(width, height);
            int save = recording.save();
            recording.clipRect(0, 0, width, height);
            frame = new Frame(view, recording, nativeDraw, nativePicture, width, height);

            try {
                committed = endpoint.dispatchReplacement(frame);

                if (frame.hasHostFailure()) {
                    committed = false;
                }
            } finally {
                frame.finish();
                recording.restoreToCount(save);
            }
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            ComposerInputRendererExtensions.report(error);
        } finally {
            if (recording != null) {
                try {
                    picture.endRecording();
                } catch (RuntimeException | LinkageError | AssertionError | OutOfMemoryError error) {
                    committed = false;
                    ComposerInputRendererExtensions.report(error);
                }
            }
        }

        if (!committed) {
            drawFallback(canvas, nativeDraw, frame);
            return;
        }

        int save = canvas.save();
        boolean replayed = false;

        try {
            canvas.clipRect(0, 0, width, height);
            picture.draw(canvas);
            replayed = true;
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            ComposerInputRendererExtensions.report(error);
        } finally {
            canvas.restoreToCount(save);
        }

        if (!replayed) {
            drawFallback(canvas, nativeDraw, frame);
        }
    }

    private void drawFallback(Canvas canvas, Consumer<Canvas> nativeDraw, Frame frame) {
        int save = canvas.save();

        try {
            canvas.clipRect(0, 0, view.getWidth(), view.getHeight());

            if (frame != null && frame.hasNativeCapture()) {
                frame.drawNativeCapture(canvas);
            } else {
                nativeDraw.accept(canvas);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    void invalidate() {
        if (!closed) {
            view.invalidate();
        }
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }

        endpoint.close();
        closed = true;
        ComposerInputRendererExtensions.removeSurface(this);
        view.invalidate();
    }

    private static final class Frame implements ComposerInputRendererExtensions.Frame,
            HostExtensionBudgetInput, HostExtensionFailureInput {
        private final View view;
        private final Canvas canvas;
        private final Consumer<Canvas> nativeDraw;
        private final Picture nativePicture;
        private final int width;
        private final int height;
        private boolean open = true;
        private boolean nativeRequested;
        private boolean nativeCaptured;
        private long excludedNanos;
        private Throwable hostFailure;

        Frame(View view, Canvas canvas, Consumer<Canvas> nativeDraw,
                Picture nativePicture, int width, int height) {
            this.view = view;
            this.canvas = canvas;
            this.nativeDraw = nativeDraw;
            this.nativePicture = nativePicture;
            this.width = width;
            this.height = height;
        }

        @Override
        public View getView() {
            requireOpen();
            return view;
        }

        @Override
        public Canvas getCanvas() {
            requireOpen();
            return canvas;
        }

        @Override
        public int getWidth() {
            requireOpen();
            return width;
        }

        @Override
        public int getHeight() {
            requireOpen();
            return height;
        }

        @Override
        public void drawNative() {
            requireOpen();

            if (nativeRequested) {
                throw new IllegalStateException("Native draw continuation was already used");
            }

            nativeRequested = true;
            long start = System.nanoTime();
            Canvas nativeCanvas = null;
            boolean captured = false;

            try {
                nativeCanvas = nativePicture.beginRecording(width, height);
                int save = nativeCanvas.save();

                try {
                    nativeCanvas.clipRect(0, 0, width, height);
                    nativeDraw.accept(nativeCanvas);
                    captured = true;
                } finally {
                    try {
                        nativeCanvas.restoreToCount(save);
                    } finally {
                        nativePicture.endRecording();
                    }
                }

                nativeCaptured = captured;

                if (captured) {
                    nativePicture.draw(canvas);
                }
            } catch (RuntimeException | Error error) {
                nativeCaptured = captured;
                hostFailure = error;
                throw error;
            } finally {
                excludedNanos += Math.max(0, System.nanoTime() - start);
            }
        }

        @Override
        public long excludedCallbackNanos() {
            return excludedNanos;
        }

        @Override
        public boolean isHostFailure(Throwable error) {
            return error == hostFailure;
        }

        void finish() {
            open = false;
        }

        boolean hasNativeCapture() {
            return nativeCaptured;
        }

        boolean hasHostFailure() {
            return hostFailure != null;
        }

        void drawNativeCapture(Canvas target) {
            nativePicture.draw(target);
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException("Composer input renderer frame has expired");
            }
        }
    }
}
