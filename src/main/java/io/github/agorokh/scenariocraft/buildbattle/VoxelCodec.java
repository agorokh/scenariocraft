package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Encodes and decodes the frozen schema-v1 x-fastest voxel layout. */
final class VoxelCodec {
    private VoxelCodec() {}

    static VoxelFile encode(RoundSnapshot.Plot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        RoundExportRequest.Plot plot = snapshot.metadata();
        List<String> source = snapshot.blockIds();

        Map<String, Integer> paletteIndices = new LinkedHashMap<>();
        paletteIndices.put(VoxelFile.AIR_BLOCK_ID, 0);
        List<Integer> blocks = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String blockId = requireNamespacedBlockId(source.get(index));
            int paletteIndex =
                    paletteIndices.computeIfAbsent(blockId, ignored -> paletteIndices.size());
            blocks.add(paletteIndex);
        }

        return new VoxelFile(
                VoxelFile.SCHEMA_VERSION,
                plot.plotId(),
                List.of(plot.originX(), plot.originY(), plot.originZ()),
                List.of(plot.sizeX(), plot.sizeY(), plot.sizeZ()),
                List.copyOf(paletteIndices.keySet()),
                blocks);
    }

    static RoundSnapshot.Plot trimAirAbove(RoundSnapshot.Plot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        RoundExportRequest.Plot plot = snapshot.metadata();
        List<String> source = snapshot.blockIds();
        int layerArea = Math.multiplyExact(plot.sizeX(), plot.sizeZ());
        int trimmedHeight = 0;
        for (int index = source.size() - 1; index >= 0; index--) {
            if (!VoxelFile.AIR_BLOCK_ID.equals(source.get(index))) {
                trimmedHeight = index / layerArea + 1;
                break;
            }
        }
        RoundExportRequest.Plot trimmedMetadata =
                new RoundExportRequest.Plot(
                        plot.plotId(),
                        plot.player(),
                        plot.originX(),
                        plot.originY(),
                        plot.originZ(),
                        plot.sizeX(),
                        trimmedHeight,
                        plot.sizeZ());
        int trimmedBlockCount = Math.multiplyExact(layerArea, trimmedHeight);
        return new RoundSnapshot.Plot(
                trimmedMetadata, source.subList(0, trimmedBlockCount));
    }

    static List<String> decode(VoxelFile voxels) {
        Objects.requireNonNull(voxels, "voxels");
        return voxels.blocks().stream().map(voxels.palette()::get).toList();
    }

    private static String requireNamespacedBlockId(String blockId) {
        Objects.requireNonNull(blockId, "blockId");
        if (!blockId.matches("[a-z0-9._-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("invalid namespaced block id: " + blockId);
        }
        return blockId;
    }
}
