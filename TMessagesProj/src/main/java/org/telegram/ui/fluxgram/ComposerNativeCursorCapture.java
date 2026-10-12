package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.Picture;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.ComposerCursorPresentation;
import org.telegram.messenger.plugins.api.ComposerCursorSnapshot;

import java.util.function.Consumer;

final class ComposerNativeCursorCapture {
    private final ComposerTextLayer layer = new ComposerTextLayer();
    private Picture picture;
    private long revision = Long.MIN_VALUE;
    private long failedRevision = Long.MIN_VALUE;

    void draw(Canvas canvas, Consumer<Canvas> nativeDraw,
            ComposerCursorSnapshot snapshot, ComposerCursorPresentation presentation) {
        if (presentation.alpha <= 0) {
            return;
        }

        if (failedRevision == snapshot.revision) {
            nativeDraw.accept(canvas);
            return;
        }

        try {
            prepare(nativeDraw, snapshot, presentation);
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            fail(snapshot.revision);
            FileLog.e(error);
            nativeDraw.accept(canvas);
            return;
        }

        int save = canvas.save();
        Throwable replayFailure = null;

        try {
            canvas.translate(snapshot.left, snapshot.top);
            ComposerNativeTextCapture.applyTransform(canvas, presentation);
            layer.draw(canvas, picture, width(snapshot), height(snapshot), presentation);
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            replayFailure = error;
            fail(snapshot.revision);
        } finally {
            try {
                canvas.restoreToCount(save);
            } catch (Exception | LinkageError | AssertionError error) {
                replayFailure = error;
                fail(snapshot.revision);
            }
        }

        if (replayFailure != null) {
            FileLog.e(replayFailure);
            nativeDraw.accept(canvas);
        }
    }

    void release() {
        picture = null;
        revision = Long.MIN_VALUE;
        failedRevision = Long.MIN_VALUE;
        layer.release();
    }

    private void prepare(Consumer<Canvas> nativeDraw, ComposerCursorSnapshot snapshot,
            ComposerCursorPresentation presentation) {
        int width = width(snapshot);
        int height = height(snapshot);

        if (picture == null || revision != snapshot.revision) {
            layer.release();
            Picture next = new Picture();
            Canvas recording = next.beginRecording(width, height);

            try {
                recording.translate(-snapshot.left, -snapshot.top);
                nativeDraw.accept(recording);
            } finally {
                next.endRecording();
            }

            picture = next;
            revision = snapshot.revision;
            failedRevision = Long.MIN_VALUE;
        }

        layer.prepare(picture, width, height, presentation);
    }

    private void fail(long revision) {
        picture = null;
        this.revision = Long.MIN_VALUE;
        failedRevision = revision;
        layer.release();
    }

    private static int width(ComposerCursorSnapshot snapshot) {
        return Math.max(1, (int) Math.ceil(snapshot.right - snapshot.left));
    }

    private static int height(ComposerCursorSnapshot snapshot) {
        return Math.max(1, (int) Math.ceil(snapshot.bottom - snapshot.top));
    }
}
