package org.telegram.ui.fluxgram;

import org.fluxgram.ComposerTextClusterSnapshot;
import org.fluxgram.ComposerTextInsertion;
import org.fluxgram.ComposerTextLayoutSnapshot;

final class ComposerTextInsertions {
    private ComposerTextInsertions() {
    }

    static ComposerTextInsertion create(ComposerTextLayoutSnapshot layout,
            int start, int removedCount, int insertedCount, boolean coalesced) {
        if (layout == null || layout.text == null || insertedCount <= 0
                || start < 0 || removedCount < 0 || start > layout.text.length()
                || insertedCount > layout.text.length() - start) {
            return null;
        }

        int end = start + insertedCount;
        int firstCluster = -1;
        int clusterCount = 0;
        int covered = start;

        for (int index = 0; index < layout.getClusterCount(); index++) {
            ComposerTextClusterSnapshot cluster = layout.getCluster(index);

            if (cluster.end <= start) {
                continue;
            }

            if (cluster.start >= end) {
                break;
            }

            if (cluster.start != covered || cluster.end <= cluster.start || cluster.end > end
                    || cluster.line < 0 || cluster.line >= layout.getLineCount()
                    || !Float.isFinite(cluster.primaryStart) || !Float.isFinite(cluster.primaryEnd)
                    || !Float.isFinite(cluster.secondaryStart) || !Float.isFinite(cluster.secondaryEnd)
                    || !Float.isFinite(cluster.advance) || cluster.advance < 0) {
                return null;
            }

            if (firstCluster < 0) {
                firstCluster = index;
            }

            covered = cluster.end;
            clusterCount++;

            if (clusterCount > ComposerTextInsertion.MAX_CLUSTERS) {
                return null;
            }
        }

        if (firstCluster < 0 || clusterCount == 0 || covered != end) {
            return null;
        }

        return new ComposerTextInsertion(layout.revision, layout, start, removedCount,
                insertedCount, coalesced, firstCluster, clusterCount);
    }
}
