package org.telegram.ui.fluxgram;

import org.telegram.messenger.plugins.api.ComposerTextClusterSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextInsertion;
import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLineSnapshot;

public final class ComposerTextInsertionsCheck {
    public static void main(String[] args) {
        ComposerTextLineSnapshot line = new ComposerTextLineSnapshot(
                0, 4, 4, 0, 40, 0, 20, 15, false);
        ComposerTextClusterSnapshot[] clusters = new ComposerTextClusterSnapshot[] {
                cluster(0, 1), cluster(1, 3), cluster(3, 4)
        };
        ComposerTextLayoutSnapshot layout = layout("aXYz", line, clusters);
        ComposerTextInsertion insertion = ComposerTextInsertions.create(layout, 1, 2, 2, false);
        require(insertion != null && insertion.revision == 7 && insertion.layout == layout
                && insertion.start == 1 && insertion.removedCount == 2 && insertion.insertedCount == 2
                && insertion.firstCluster == 1 && insertion.clusterCount == 1 && !insertion.coalesced,
                "safe replacement insertion rejected");
        require(ComposerTextInsertions.create(layout, 2, 0, 1, false) == null,
                "partial cluster insertion accepted");
        require(ComposerTextInsertions.create(layout("aXz", line,
                new ComposerTextClusterSnapshot[] {cluster(0, 1), cluster(2, 3)}), 0, 0, 3, false) == null,
                "cluster gap accepted");
        require(ComposerTextInsertions.create(layout, 1, 0, 0, false) == null,
                "empty insertion accepted");

        ComposerTextClusterSnapshot[] many = new ComposerTextClusterSnapshot[65];
        StringBuilder text = new StringBuilder();

        for (int index = 0; index < many.length; index++) {
            many[index] = cluster(index, index + 1);
            text.append('a');
        }

        ComposerTextLineSnapshot longLine = new ComposerTextLineSnapshot(
                0, many.length, many.length, 0, many.length * 10, 0, 20, 15, false);
        require(ComposerTextInsertions.create(layout(text.toString(), longLine, many),
                0, 0, many.length, true) == null, "unbounded cluster insertion accepted");

        ComposerTextClusterSnapshot invalid = new ComposerTextClusterSnapshot(
                0, 1, 0, false, Float.NaN, 10, 0, 10, 10);
        require(ComposerTextInsertions.create(layout("a", line,
                new ComposerTextClusterSnapshot[] {invalid}), 0, 0, 1, false) == null,
                "invalid cluster geometry accepted");
    }

    private static ComposerTextLayoutSnapshot layout(String text,
            ComposerTextLineSnapshot line, ComposerTextClusterSnapshot[] clusters) {
        return new ComposerTextLayoutSnapshot(
                7, text, text.length(), text.length(), -1, -1,
                320, 50, 0, 0, 0, 0, false, false, false,
                new ComposerTextLineSnapshot[] {line}, clusters);
    }

    private static ComposerTextClusterSnapshot cluster(int start, int end) {
        return new ComposerTextClusterSnapshot(
                start, end, 0, false, start * 10, end * 10,
                start * 10, end * 10, (end - start) * 10);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
