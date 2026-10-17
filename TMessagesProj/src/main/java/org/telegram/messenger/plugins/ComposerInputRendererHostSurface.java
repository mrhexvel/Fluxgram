package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.Looper;
import android.view.View;

import org.fluxgram.ComposerExpandedBounds;
import org.fluxgram.ComposerGeometry;
import org.fluxgram.ComposerInput;
import org.fluxgram.ComposerSendEvent;
import org.fluxgram.ComposerTextCapture;
import org.fluxgram.ComposerTextClusterCapture;
import org.fluxgram.ComposerTextClusterPresentation;
import org.fluxgram.ComposerTextClusterSelection;
import org.fluxgram.ComposerTextClusterSnapshot;
import org.fluxgram.ComposerTextLayers;
import org.fluxgram.ComposerTextLayoutChange;
import org.fluxgram.ComposerTextPresentation;

import java.util.function.BiFunction;
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
    private final BiFunction<ComposerTextClusterSelection, String,
            TextClusterCapture> captureTextClusters;
    private final Picture picture = new Picture();
    private final Picture nativePicture = new Picture();
    private final HostExtensionRegistry.Endpoint<Object, Void> endpoint;
    private int capabilities;
    private long sendSequence;
    private long animationStarted;
    private boolean frameRequested;
    private ComposerTextLayoutChange activeLayoutChange;
    private ComposerTextClusterSelection selectedTextClusters;
    private TextClusterCapture textClusterCapture;
    private RemovedTextClusterCapture removedTextClusterCapture;
    private boolean closed;

    private ComposerInputRendererHostSurface(View view, int account, long ownerId, long dialogId,
            Supplier<ComposerGeometry> geometry, BooleanSupplier active, Runnable invalidate,
            BiFunction<ComposerTextClusterSelection, String,
                    TextClusterCapture> captureTextClusters) {
        if (view == null || geometry == null || active == null || invalidate == null
                || captureTextClusters == null) {
            throw new IllegalArgumentException("Composer input renderer surface is incomplete");
        }

        this.view = view;
        this.account = account;
        this.ownerId = ownerId;
        this.dialogId = dialogId;
        this.geometry = geometry;
        this.active = active;
        this.invalidate = invalidate;
        this.captureTextClusters = captureTextClusters;
        endpoint = ComposerInputRendererExtensions.openEndpoint();
        ComposerInputRendererExtensions.addSurface(this);
        refreshRegistration();
    }

    public static ComposerInputRendererHostSurface attach(View view, int account,
            long ownerId, long dialogId, Supplier<ComposerGeometry> geometry,
            BooleanSupplier active, Runnable invalidate,
            BiFunction<ComposerTextClusterSelection, String,
                    TextClusterCapture> captureTextClusters) {
        return new ComposerInputRendererHostSurface(
                view, account, ownerId, dialogId, geometry, active, invalidate,
                captureTextClusters);
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

    @Override
    public void selectTextClusters(ComposerTextClusterSelection selection) {
        requireMainThread();

        if (!hasCapability(ComposerInputRendererExtensions.CAPABILITY_TEXT_CAPTURE)
                || activeLayoutChange == null) {
            throw new IllegalStateException(
                    "Composer text clusters can only be selected during a layout callback");
        }

        validateClusterSelection(selection, activeLayoutChange);
        selectedTextClusters = selection;
    }

    public boolean hasCapability(int capability) {
        return isActive() && (capabilities & capability) != 0;
    }

    public void input(ComposerInput input) {
        if (input != null && hasCapability(ComposerInputRendererExtensions.CAPABILITY_INPUT)) {
            dispatch(new Invocation(this, ComposerInputRendererExtensions.INVOCATION_INPUT, input));
        }
    }

    public void textLayoutChanged(
            ComposerTextLayoutChange change, RemovedTextClusterCapture removedCapture) {
        boolean acceptedRemovedCapture = false;

        if (change != null
                && hasCapability(ComposerInputRendererExtensions.CAPABILITY_TEXT_LAYOUT)) {
            clearTextCaptures();
            activeLayoutChange = change;

            try {
                dispatch(new Invocation(
                        this, ComposerInputRendererExtensions.INVOCATION_TEXT_LAYOUT, change));
            } finally {
                activeLayoutChange = null;
            }

            if (endpoint.hasReplacement()) {
                if (selectedTextClusters != null && change.after != null) {
                    try {
                        textClusterCapture = captureTextClusters.apply(
                                selectedTextClusters, change.after.text);
                    } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
                        ComposerInputRendererExtensions.report(error);
                    }
                }

                removedTextClusterCapture = removedCapture;
                acceptedRemovedCapture = removedCapture != null;
            }

            selectedTextClusters = null;
        }

        if (!acceptedRemovedCapture && removedCapture != null) {
            removedCapture.release();
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
        int scrollX = view.getScrollX();
        int scrollY = view.getScrollY();

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
            frame = new Frame(this, recording, nativeDraw, nativePicture,
                    width, height, scrollX, scrollY);

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
            canvas.translate(scrollX, scrollY);
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
        clearTextCaptures();

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
            if (frame != null && frame.hasNativeCapture()) {
                frame.drawNativeCapture(canvas);
            } else {
                int scrollX = frame == null ? view.getScrollX() : frame.scrollX;
                int scrollY = frame == null ? view.getScrollY() : frame.scrollY;
                canvas.clipRect(scrollX, scrollY,
                        scrollX + view.getWidth(), scrollY + view.getHeight());
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
        clearTextCaptures();
        ComposerInputRendererExtensions.removeSurface(this);
        invalidate.run();
    }

    private void clearTextCaptures() {
        selectedTextClusters = null;

        if (textClusterCapture != null) {
            textClusterCapture.release();
            textClusterCapture = null;
        }

        if (removedTextClusterCapture != null) {
            removedTextClusterCapture.release();
            removedTextClusterCapture = null;
        }
    }

    private static void validateClusterSelection(
            ComposerTextClusterSelection selection, ComposerTextLayoutChange change) {
        if (selection == null || change.after == null
                || selection.revision != change.revision
                || selection.revision != change.after.revision
                || selection.getCount() < 1
                || selection.getCount() > ComposerTextClusterSelection.MAX_CLUSTERS) {
            throw new IllegalArgumentException("Invalid composer text cluster selection");
        }

        int clusterIndex = 0;
        int previousEnd = -1;

        for (int index = 0; index < selection.getCount(); index++) {
            org.fluxgram.ComposerTextCandidate candidate =
                    selection.getCandidate(index);

            if (candidate == null || candidate.revision != selection.revision
                    || candidate.start < 0 || candidate.start >= candidate.end
                    || candidate.end > change.after.text.length()
                    || candidate.start < previousEnd) {
                throw new IllegalArgumentException("Invalid composer text cluster range");
            }

            while (clusterIndex < change.after.getClusterCount()
                    && change.after.getCluster(clusterIndex).end <= candidate.start) {
                clusterIndex++;
            }

            if (clusterIndex >= change.after.getClusterCount()) {
                throw new IllegalArgumentException("Composer text cluster range is unavailable");
            }

            ComposerTextClusterSnapshot cluster = change.after.getCluster(clusterIndex);

            if (cluster.start != candidate.start || cluster.end != candidate.end) {
                throw new IllegalArgumentException("Composer text range does not match a cluster");
            }

            previousEnd = candidate.end;
            clusterIndex++;
        }
    }

    private static void validateTextPresentation(
            ComposerTextPresentation presentation, ComposerTextCapture capture,
            ComposerGeometry geometry) {
        float width = capture.getRight() - capture.getLeft();
        float height = capture.getBottom() - capture.getTop();
        float limit = Math.max(geometry.width, geometry.height) * 2f;

        if (presentation == null
                || !Float.isFinite(presentation.translationX)
                || !Float.isFinite(presentation.translationY)
                || !Float.isFinite(width) || width <= 0 || width > 256
                || !Float.isFinite(height) || height <= 0 || height > 256
                || !Float.isFinite(presentation.alpha)
                || presentation.alpha < 0 || presentation.alpha > 1
                || !Float.isFinite(presentation.scaleX)
                || presentation.scaleX < 0 || presentation.scaleX > 4
                || !Float.isFinite(presentation.scaleY)
                || presentation.scaleY < 0 || presentation.scaleY > 4
                || !Float.isFinite(presentation.rotationDegrees)
                || Math.abs(presentation.rotationDegrees) > 360
                || !Float.isFinite(presentation.pivotX)
                || presentation.pivotX < 0 || presentation.pivotX > width
                || !Float.isFinite(presentation.pivotY)
                || presentation.pivotY < 0 || presentation.pivotY > height
                || !Float.isFinite(presentation.tintAmount)
                || presentation.tintAmount < 0 || presentation.tintAmount > 1
                || presentation.tintAmount > 0
                && (presentation.tintColor >>> 24) != 0xFF
                || !Float.isFinite(presentation.blurRadius)
                || presentation.blurRadius < 0
                || presentation.blurRadius > ComposerTextPresentation.MAX_BLUR_RADIUS
                || Math.abs(presentation.translationX) > limit
                || Math.abs(presentation.translationY) > limit
                || !validTransformedBounds(
                        presentation, capture.getLeft(), capture.getTop(),
                        width, height, limit, geometry)) {
            throw new IllegalArgumentException("Invalid composer text presentation");
        }
    }

    private static boolean validTransformedBounds(
            ComposerTextPresentation presentation, float left, float top,
            float width, float height, float limit, ComposerGeometry geometry) {
        double radians = Math.toRadians(presentation.rotationDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (int corner = 0; corner < 4; corner++) {
            float outset = presentation.blurRadius > 0
                    ? (float) Math.ceil(presentation.blurRadius) + 1 : 0;
            float x = (corner & 1) == 0 ? -outset : width + outset;
            float y = (corner & 2) == 0 ? -outset : height + outset;
            double scaledX = (x - presentation.pivotX) * presentation.scaleX;
            double scaledY = (y - presentation.pivotY) * presentation.scaleY;
            float transformedX = (float) (left + presentation.translationX
                    + presentation.pivotX + scaledX * cosine - scaledY * sine);
            float transformedY = (float) (top + presentation.translationY
                    + presentation.pivotY + scaledX * sine + scaledY * cosine);

            if (!Float.isFinite(transformedX) || !Float.isFinite(transformedY)) {
                return false;
            }

            minX = Math.min(minX, transformedX);
            minY = Math.min(minY, transformedY);
            maxX = Math.max(maxX, transformedX);
            maxY = Math.max(maxY, transformedY);
        }

        return maxX - minX <= limit && maxY - minY <= limit
                && minX >= -limit && maxX <= geometry.width + limit
                && minY >= -limit && maxY <= geometry.height + limit;
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
        private final int scrollX;
        private final int scrollY;
        private boolean open = true;
        private boolean nativeRequested;
        private boolean nativeCaptured;
        private boolean captureRejected;
        private long excludedNanos;
        private Throwable hostFailure;

        Frame(ComposerInputRendererHostSurface surface, Canvas canvas,
                Consumer<Canvas> nativeDraw, Picture nativePicture,
                int width, int height, int scrollX, int scrollY) {
            super(surface, ComposerInputRendererExtensions.INVOCATION_DRAW, null);
            this.canvas = canvas;
            this.nativeDraw = nativeDraw;
            this.nativePicture = nativePicture;
            this.width = width;
            this.height = height;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
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
        public ComposerTextClusterCapture getTextClusterCapture() {
            requireOpen();
            return surface.textClusterCapture;
        }

        @Override
        public ComposerTextClusterCapture getRemovedTextClusterCapture() {
            requireOpen();
            return surface.removedTextClusterCapture;
        }

        @Override
        public void drawNative() {
            drawNative(null, null);
        }

        @Override
        public void drawNative(ComposerTextClusterPresentation current,
                ComposerTextClusterPresentation removed) {
            requireOpen();

            if (nativeRequested) {
                throw new IllegalStateException("Native draw continuation was already used");
            }

            nativeRequested = true;
            long start = System.nanoTime();

            try {
                if ((current != null && (surface.textClusterCapture == null
                        || !surface.textClusterCapture.isValid()))
                        || (removed != null && (surface.removedTextClusterCapture == null
                        || !surface.removedTextClusterCapture.isValid()))) {
                    captureRejected = true;
                    return;
                }

                validatePresentations(current, removed);

                if (current != null) {
                    surface.textClusterCapture.draw(canvas, this::drawNativeTo, current);
                } else {
                    drawNativeTo(canvas);
                }

                if (removed != null) {
                    surface.removedTextClusterCapture.draw(canvas, removed);
                }
            } finally {
                excludedNanos += Math.max(0, System.nanoTime() - start);
            }
        }

        private void drawNativeTo(Canvas target) {
            Canvas nativeCanvas = null;
            boolean captured = false;

            try {
                nativeCanvas = nativePicture.beginRecording(width, height);
                int save = nativeCanvas.save();

                try {
                    nativeCanvas.clipRect(0, 0, width, height);
                    nativeCanvas.translate(-scrollX, -scrollY);
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
                    nativePicture.draw(target);
                }
            } catch (RuntimeException | Error error) {
                nativeCaptured = captured;
                hostFailure = error;
                throw error;
            }
        }

        private void validatePresentations(ComposerTextClusterPresentation current,
                ComposerTextClusterPresentation removed) {
            ComposerGeometry geometry = surface.getGeometry();

            if (current != null) {
                validateClusterPresentation(current, surface.textClusterCapture, geometry,
                        "Invalid composer text cluster presentation");
            }

            if (removed != null) {
                validateClusterPresentation(removed, surface.removedTextClusterCapture, geometry,
                        "Invalid removed composer text cluster presentation");
            }
        }

        private void validateClusterPresentation(ComposerTextClusterPresentation presentation,
                ComposerTextClusterCapture capture, ComposerGeometry geometry, String error) {
            if (capture == null || presentation.revision != capture.getRevision()
                    || presentation.getCount() != capture.getCount()) {
                throw new IllegalArgumentException(error);
            }

            for (int index = 0; index < presentation.getCount(); index++) {
                ComposerTextLayers layers = presentation.getLayers(index);

                if (layers == null) {
                    continue;
                }

                validateLayers(layers, capture.getCapture(index), geometry);
            }
        }

        private void validateLayers(ComposerTextLayers layers,
                ComposerTextCapture capture, ComposerGeometry geometry) {
            if (layers.getCount() < 1 || layers.getCount() > ComposerTextLayers.MAX_LAYERS) {
                throw new IllegalArgumentException("Invalid composer text layer count");
            }

            for (int index = 0; index < layers.getCount(); index++) {
                ComposerTextPresentation presentation = layers.getPresentation(index);

                if (presentation != null) {
                    validateTextPresentation(presentation, capture, geometry);
                }
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
            return hostFailure != null || captureRejected;
        }

        void drawNativeCapture(Canvas target) {
            int save = target.save();

            try {
                target.translate(scrollX, scrollY);
                target.clipRect(0, 0, width, height);
                nativePicture.draw(target);
            } finally {
                target.restoreToCount(save);
            }
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

    public interface TextClusterCapture extends ComposerTextClusterCapture {
        boolean isValid();

        void draw(Canvas canvas, Consumer<Canvas> nativeDraw,
                ComposerTextClusterPresentation presentation);

        default void release() {
        }
    }

    public interface RemovedTextClusterCapture extends ComposerTextClusterCapture {
        boolean isValid();

        void draw(Canvas canvas, ComposerTextClusterPresentation presentation);

        default void release() {
        }
    }
}
