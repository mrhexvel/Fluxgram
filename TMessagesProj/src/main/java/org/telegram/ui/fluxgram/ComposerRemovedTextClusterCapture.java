package org.telegram.ui.fluxgram;

import android.graphics.Canvas;

import org.telegram.messenger.plugins.ComposerInputRendererHostSurface;
import org.fluxgram.ComposerTextCapture;
import org.fluxgram.ComposerTextClusterPresentation;
import org.fluxgram.ComposerTextClusterSnapshot;
import org.fluxgram.ComposerTextLayoutSnapshot;
import org.fluxgram.ComposerTextLayers;
import org.telegram.ui.Components.EditTextCaption;

import java.util.ArrayList;

public final class ComposerRemovedTextClusterCapture
        implements ComposerInputRendererHostSurface.RemovedTextClusterCapture {
    private static final int MAX_CLUSTERS = 64;
    private static final int MAX_SOURCE_AREA = 256 * 256;
    private static final int MAX_PADDED_AREA = 256 * 1024;

    private final long revision;
    private final ComposerRemovedTextCapture[] captures;

    private ComposerRemovedTextClusterCapture(
            long revision, ComposerRemovedTextCapture[] captures) {
        this.revision = revision;
        this.captures = captures;
    }

    public static ComposerRemovedTextClusterCapture capture(
            EditTextCaption field, ComposerTextLayoutSnapshot snapshot,
            long revision, int start, int end) {
        if (field == null || snapshot == null || snapshot.text == null
                || snapshot.unsupportedClusters || start < 0 || start >= end
                || end > snapshot.text.length()) {
            return null;
        }

        ArrayList<ComposerRemovedTextCapture> captures = new ArrayList<>();
        int sourceArea = 0;
        int paddedArea = 0;

        for (int index = 0; index < snapshot.getClusterCount(); index++) {
            ComposerTextClusterSnapshot cluster = snapshot.getCluster(index);

            if (cluster.end <= start || cluster.start >= end) {
                continue;
            }

            if (cluster.start < start || cluster.end > end
                    || captures.size() >= MAX_CLUSTERS) {
                release(captures);
                return null;
            }

            ComposerRemovedTextCapture capture = ComposerRemovedTextCapture.capture(
                    field, snapshot.revision, snapshot.text, cluster.start, cluster.end);

            if (capture == null) {
                release(captures);
                return null;
            }

            captures.add(capture);
            sourceArea += capture.area();
            paddedArea += capture.paddedArea();

            if (sourceArea > MAX_SOURCE_AREA || paddedArea > MAX_PADDED_AREA) {
                release(captures);
                return null;
            }
        }

        if (captures.isEmpty()) {
            return null;
        }

        return new ComposerRemovedTextClusterCapture(
                revision, captures.toArray(new ComposerRemovedTextCapture[0]));
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
        for (ComposerRemovedTextCapture capture : captures) {
            if (!capture.isValid()) {
                return false;
            }
        }

        return captures.length > 0;
    }

    @Override
    public void draw(Canvas canvas, ComposerTextClusterPresentation presentation) {
        if (!isValid() || presentation == null
                || presentation.revision != revision
                || presentation.getCount() != captures.length) {
            return;
        }

        for (int index = 0; index < captures.length; index++) {
            ComposerTextLayers layers = presentation.getLayers(index);

            if (layers != null) {
                captures[index].draw(canvas, layers);
            }
        }
    }

    @Override
    public void release() {
        for (ComposerRemovedTextCapture capture : captures) {
            capture.release();
        }
    }

    private static void release(ArrayList<ComposerRemovedTextCapture> captures) {
        for (ComposerRemovedTextCapture capture : captures) {
            capture.release();
        }
    }
}
