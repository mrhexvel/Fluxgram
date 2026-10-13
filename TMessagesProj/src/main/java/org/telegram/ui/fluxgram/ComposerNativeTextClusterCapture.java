package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.util.Log;

import org.telegram.messenger.plugins.PluginComposerRegistry;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextCapture;
import org.telegram.messenger.plugins.api.ComposerTextClusterPresentation;
import org.telegram.messenger.plugins.api.ComposerTextClusterSelection;
import org.telegram.messenger.plugins.api.ComposerTextLayers;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;
import org.telegram.ui.Components.EditTextCaption;

import java.util.function.Consumer;

public final class ComposerNativeTextClusterCapture implements PluginComposerRegistry.TextClusterCapture {
    private static final int MAX_SOURCE_AREA = 256 * 256;
    private static final int MAX_PADDED_AREA = 256 * 1024;

    private final long revision;
    private final ComposerNativeTextCapture[] captures;
    private final Picture frame = new Picture();
    private boolean failed;

    private ComposerNativeTextClusterCapture(long revision, ComposerNativeTextCapture[] captures) {
        this.revision = revision;
        this.captures = captures;
    }

    public static ComposerNativeTextClusterCapture capture(
            EditTextCaption field, ComposerTextClusterSelection selection, String expectedText) {
        if (selection == null || selection.getCount() < 1
                || selection.getCount() > ComposerTextClusterSelection.MAX_CLUSTERS) {
            return null;
        }

        ComposerNativeTextCapture[] captures = new ComposerNativeTextCapture[selection.getCount()];
        int sourceArea = 0;
        int paddedArea = 0;

        for (int index = 0; index < captures.length; index++) {
            ComposerNativeTextCapture capture;

            try {
                ComposerTextCandidate candidate = selection.getCandidate(index);
                capture = ComposerNativeTextCapture.capture(field, candidate, expectedText, true);
            } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
                release(captures);
                Log.e("FluxGramComposer", "Native text cluster capture failed", error);
                return null;
            }

            if (capture == null) {
                release(captures);
                return null;
            }

            captures[index] = capture;
            sourceArea += capture.area();
            paddedArea += capture.paddedArea();

            if (sourceArea > MAX_SOURCE_AREA || paddedArea > MAX_PADDED_AREA) {
                release(captures);
                return null;
            }
        }

        return new ComposerNativeTextClusterCapture(selection.revision, captures);
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public int getCount() {
        return captures.length;
    }

    @Override
    public ComposerTextCapture getCapture(int index) {
        return captures[index];
    }

    @Override
    public boolean isValid() {
        if (failed || captures.length == 0 || !captures[0].isValid()) {
            return false;
        }

        for (int index = 1; index < captures.length; index++) {
            if (!captures[index].hasSameStateAs(captures[0])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void draw(Canvas canvas, Consumer<Canvas> nativeDraw,
            ComposerTextClusterPresentation presentation) {
        if (failed || presentation == null || presentation.revision != revision
                || presentation.getCount() != captures.length) {
            nativeDraw.accept(canvas);
            return;
        }

        boolean active = false;

        for (int index = 0; index < captures.length; index++) {
            ComposerTextLayers layers = presentation.getLayers(index);

            if (layers != null) {
                for (int layerIndex = 0; layerIndex < layers.getCount(); layerIndex++) {
                    if (layers.getPresentation(layerIndex) != null) {
                        active = true;
                    }
                }
            }
        }

        if (!active) {
            nativeDraw.accept(canvas);
            return;
        }

        Canvas recording;
        boolean recordingOpen = false;

        try {
            recording = frame.beginRecording(captures[0].fieldWidth(), captures[0].fieldHeight());
            recordingOpen = true;

            for (int index = 0; index < captures.length; index++) {
                ComposerTextLayers layers = presentation.getLayers(index);

                if (layers == null) {
                    continue;
                }

                for (int layerIndex = 0; layerIndex < layers.getCount(); layerIndex++) {
                    ComposerTextPresentation item = layers.getPresentation(layerIndex);

                    if (item != null && (!captures[index].prepare(item)
                            || !captures[index].replay(recording, item))) {
                        failed = true;
                        nativeDraw.accept(canvas);
                        return;
                    }
                }
            }

            frame.endRecording();
            recordingOpen = false;
            canvas.drawPicture(frame);
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            failed = true;
            Log.e("FluxGramComposer", "Native text cluster replay failed", error);
            nativeDraw.accept(canvas);
            return;
        } finally {
            if (recordingOpen) {
                try {
                    frame.endRecording();
                } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
                    failed = true;
                    Log.e("FluxGramComposer", "Native text cluster recording cleanup failed", error);
                }
            }
        }

        int save = canvas.save();

        try {
            for (int index = 0; index < captures.length; index++) {
                ComposerTextLayers layers = presentation.getLayers(index);

                if (layers == null) {
                    continue;
                }

                for (int layerIndex = 0; layerIndex < layers.getCount(); layerIndex++) {
                    if (layers.getPresentation(layerIndex) != null) {
                        captures[index].clipNative(canvas);
                        break;
                    }
                }
            }

            nativeDraw.accept(canvas);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    @Override
    public void release() {
        release(captures);
    }

    private static void release(ComposerNativeTextCapture[] captures) {
        for (ComposerNativeTextCapture capture : captures) {
            if (capture != null) {
                capture.release();
            }
        }
    }
}
