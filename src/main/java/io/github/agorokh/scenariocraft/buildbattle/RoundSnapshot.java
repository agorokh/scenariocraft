package io.github.agorokh.scenariocraft.buildbattle;

import java.util.List;
import java.util.Objects;

/** Complete immutable world snapshot safe for off-thread encoding and disk I/O. */
record RoundSnapshot(
        String roundId, String task, String world, List<Plot> plots) {
    RoundSnapshot {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(world, "world");
        plots = List.copyOf(plots);
    }

    record Plot(RoundExportRequest.Plot metadata, List<String> blockIds) {
        Plot {
            Objects.requireNonNull(metadata, "metadata");
            blockIds = List.copyOf(blockIds);
            if (blockIds.size() != metadata.blockCount()) {
                throw new IllegalArgumentException(
                        "snapshot block count must equal the untrimmed plot volume");
            }
        }

    }
}
