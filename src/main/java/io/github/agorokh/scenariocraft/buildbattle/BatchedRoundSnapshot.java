package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads a round's plot blocks in bounded batches on the server thread. */
final class BatchedRoundSnapshot {
    private final String roundId;
    private final RoundExportRequest request;
    private final List<List<String>> plotBlocks;
    private int plotIndex;
    private int blockIndex;

    BatchedRoundSnapshot(String roundId, RoundExportRequest request) {
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.request = Objects.requireNonNull(request, "request");
        List<List<String>> blocks = new ArrayList<>(request.plots().size());
        for (RoundExportRequest.Plot plot : request.plots()) {
            blocks.add(new ArrayList<>(Math.toIntExact(plot.blockCount())));
        }
        this.plotBlocks = List.copyOf(blocks);
    }

    int runBatch(BlockIdReader reader, int readsPerTick) {
        Objects.requireNonNull(reader, "reader");
        if (readsPerTick <= 0) {
            throw new IllegalArgumentException("readsPerTick must be positive");
        }
        int completed = 0;
        while (completed < readsPerTick && !isComplete()) {
            RoundExportRequest.Plot plot = request.plots().get(plotIndex);
            int layerArea = Math.multiplyExact(plot.sizeX(), plot.sizeZ());
            int localX = blockIndex % plot.sizeX();
            int localZ = (blockIndex / plot.sizeX()) % plot.sizeZ();
            int localY = blockIndex / layerArea;
            String blockId =
                    Objects.requireNonNull(
                            reader.read(
                                    Math.addExact(plot.originX(), localX),
                                    Math.addExact(plot.originY(), localY),
                                    Math.addExact(plot.originZ(), localZ)),
                            "block id");
            plotBlocks.get(plotIndex).add(blockId);
            blockIndex++;
            completed++;
            if (blockIndex == plot.blockCount()) {
                plotIndex++;
                blockIndex = 0;
            }
        }
        return completed;
    }

    boolean isComplete() {
        return plotIndex == request.plots().size();
    }

    RoundSnapshot finish() {
        if (!isComplete()) {
            throw new IllegalStateException("round snapshot is not complete");
        }
        List<RoundSnapshot.Plot> plots = new ArrayList<>(request.plots().size());
        for (int index = 0; index < request.plots().size(); index++) {
            plots.add(new RoundSnapshot.Plot(request.plots().get(index), plotBlocks.get(index)));
        }
        return new RoundSnapshot(roundId, request.task(), request.world(), plots);
    }

    @FunctionalInterface
    interface BlockIdReader {
        String read(int x, int y, int z);
    }
}
