package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.Looper;
import android.view.View;

import org.telegram.messenger.plugins.api.ComposerExpandedBounds;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSendEvent;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ComposerInputRendererHostSurface implements
        ComposerInputRendererExtensions.Surface, AutoCloseable {
    private static final int MAX_DIMENSION = 2048;
    private static final int MAX_AREA = 2 * 1024 * 1024;
    private static final long MAX_ANIMATION_NANOS = 2_000_000_000L;

    private final View view;
    private final int account;
    private final long ownerId;
    private final long dialogId;
    private final Supplier<ComposerGeometry> geometry;
    private final BooleanSupplier active;
    private final Runnable invalidate;
    private final Picture picture = new Picture();
    private final Picture nativePicture = new Picture();
    private final HostExtensionRegistry.Endpoint<Object, Void> endpoint;
    private int capabilities;
    private long sendSequence;
    private long animationStarted;
    private boolean frameRequested;
    private boolean closed;

    private ComposerInputRendererHostSurface(View view, int account, long ownerId, long dialogId,
            Supplier<ComposerGeometry> geometry, BooleanSupplier active, Runnable invalidate) {
        if (view == null || geometry == null || active == null || invalidate == null) {
            throw new IllegalArgumentException("Composer input renderer surface is incomplete");
        }

        this.view = view;
        this.account = account;
        this.ownerId = ownerId;
        this.dialogId = dialogId;
        this.geometry = geometry;
        this.active = active;
        this.invalidate = invalidate;
        endpoint = ComposerInputRendererExtensions.openEndpoint();
        ComposerInputRendererExtensions.addSurface(this);
        refreshRegistration();
    }

    public static ComposerInputRendererHostSurface attach(View view, int account,
            long ownerId, long dialogId, Supplier<ComposerGeometry> geometry,
            BooleanSupplier active, Runnable invalidate) {
        return new ComposerInputRendererHostSurface(
                view, account, ownerId, dialogId, geometry, active, invalidate);
    }

    @Override
    public int getAccount() {
        return account;
    }

    @Override
    public long getOwnerId() {
        return ownerId;
    }

    @Override
    public long getDialogId() {
        return dialogId;
    }

    @Override
    public ComposerGeometry getGeometry() {
        requireMainThread();

        if (!isActive()) {
            throw new IllegalStateException("Composer input renderer surface is inactive");
        }

        return geometry.get();
    }

    @Override
    public boolean isActive() {
        return !closed && endpoint.hasReplacement() && active.getAsBoolean();
    }

    @Override
    public void requestFrame() {
        requireMainThread();

        if (!isActive()) {
            return;
        }

        if (!frameRequested) {
            animationStarted = System.nanoTime();
        }

        frameRequested = true;
        invalidate.run();
    }

    public boolean hasCapability(int capability) {
        return isActive() && (capabilities & capability) != 0;
    }

    public void input(ComposerInput input) {
        if (input != null && hasCapability(ComposerInputRendererExtensions.CAPABILITY_INPUT)) {
            dispatch(new Invocation(this, ComposerInputRendererExtensions.INVOCATION_INPUT, input));
        }
    }

    public void textLayoutChanged(ComposerTextLayoutChange change) {
        if (change != null
                && hasCapability(ComposerInputRendererExtensions.CAPABILITY_TEXT_LAYOUT)) {
            dispatch(new Invocation(
                    this, ComposerInputRendererExtensions.INVOCATION_TEXT_LAYOUT, change));
        }
    }

    public void sent(boolean hasTarget, float targetX, float targetY) {
        if (hasCapability(ComposerInputRendererExtensions.CAPABILITY_SEND)) {
            dispatch(new Invocation(this, ComposerInputRendererExtensions.INVOCATION_SEND,
                    new ComposerSendEvent(++sendSequence, hasTarget, targetX, targetY)));
        }
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

        dispatchFrame();
        Canvas recording = null;
        Frame frame = null;
        boolean committed = false;

        try {
            recording = picture.beginRecording(width, height);
            int save = recording.save();
            recording.clipRect(0, 0, width, height);
            frame = new Frame(this, recording, nativeDraw, nativePicture, width, height);

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

    public void drawExpanded(Canvas canvas, ComposerExpandedBounds bounds) {
        if (canvas == null || bounds == null
                || !hasCapability(ComposerInputRendererExtensions.CAPABILITY_EXPANDED_DRAW)) {
            return;
        }

        ExpandedFrame frame = new ExpandedFrame(this, canvas, bounds);

        try {
            endpoint.dispatchReplacement(frame);
        } finally {
            frame.finish();
        }
    }

    void refreshRegistration() {
        if (closed) {
            return;
        }

        capabilities = 0;

        if (endpoint.hasReplacement()) {
            dispatch(new Invocation(this, ComposerInputRendererExtensions.INVOCATION_INITIALIZE, null));
        }

        invalidate.run();
    }

    void setCapabilities(int capabilities) {
        this.capabilities = capabilities;
    }

    private void dispatchFrame() {
        if (!frameRequested || !isActive()) {
            return;
        }

        long now = System.nanoTime();
        frameRequested = false;

        if (now - animationStarted > MAX_ANIMATION_NANOS) {
            animationStarted = 0;
            return;
        }

        boolean again = endpoint.dispatchReplacement(
                new Invocation(this, ComposerInputRendererExtensions.INVOCATION_FRAME, null, now));

        if (again && isActive()) {
            frameRequested = true;
            invalidate.run();
        } else {
            animationStarted = 0;
        }
    }

    private void dispatch(Invocation invocation) {
        requireMainThread();

        if (!closed && endpoint.hasReplacement()) {
            endpoint.dispatchReplacement(invocation);
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

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Composer input renderer requires the main thread");
        }
    }

    @Override
    public void close() throws Exception {
        requireMainThread();

        if (closed) {
            return;
        }

        endpoint.close();
        closed = true;
        frameRequested = false;
        capabilities = 0;
        ComposerInputRendererExtensions.removeSurface(this);
        invalidate.run();
    }

    static class Invocation {
        final ComposerInputRendererHostSurface surface;
        final int type;
        final Object value;
        final long frameTimeNanos;

        Invocation(ComposerInputRendererHostSurface surface, int type, Object value) {
            this(surface, type, value, 0);
        }

        Invocation(ComposerInputRendererHostSurface surface, int type,
                Object value, long frameTimeNanos) {
            this.surface = surface;
            this.type = type;
            this.value = value;
            this.frameTimeNanos = frameTimeNanos;
        }
    }

    private static final class Frame extends Invocation implements ComposerInputRendererExtensions.Frame,
            HostExtensionBudgetInput, HostExtensionFailureInput {
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

        Frame(ComposerInputRendererHostSurface surface, Canvas canvas,
                Consumer<Canvas> nativeDraw, Picture nativePicture, int width, int height) {
            super(surface, ComposerInputRendererExtensions.INVOCATION_DRAW, null);
            this.canvas = canvas;
            this.nativeDraw = nativeDraw;
            this.nativePicture = nativePicture;
            this.width = width;
            this.height = height;
        }

        @Override
        public Canvas getCanvas() {
            requireOpen();
            return canvas;
        }

        @Override
        public ComposerGeometry getGeometry() {
            requireOpen();
            return surface.getGeometry();
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

    private static final class ExpandedFrame extends Invocation
            implements ComposerInputRendererExtensions.ExpandedFrame {
        private final Canvas canvas;
        private final ComposerExpandedBounds bounds;
        private boolean open = true;

        ExpandedFrame(ComposerInputRendererHostSurface surface,
                Canvas canvas, ComposerExpandedBounds bounds) {
            super(surface, ComposerInputRendererExtensions.INVOCATION_EXPANDED_DRAW, null);
            this.canvas = canvas;
            this.bounds = bounds;
        }

        @Override
        public Canvas getCanvas() {
            requireOpen();
            return canvas;
        }

        @Override
        public ComposerGeometry getGeometry() {
            requireOpen();
            return surface.getGeometry();
        }

        @Override
        public ComposerExpandedBounds getBounds() {
            requireOpen();
            return bounds;
        }

        void finish() {
            open = false;
        }

        private void requireOpen() {
            if (!open) {
                throw new IllegalStateException("Composer expanded renderer frame has expired");
            }
        }
    }
}
