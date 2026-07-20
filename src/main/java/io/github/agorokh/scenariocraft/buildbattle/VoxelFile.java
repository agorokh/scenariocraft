package io.github.agorokh.scenariocraft.buildbattle;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Objects;

/** Frozen schema-v1 representation of one plot's voxel file. */
record VoxelFile(
        int schema,
        @SerializedName("plot_id") String plotId,
        List<Integer> origin,
        List<Integer> size,
        List<String> palette,
        List<Integer> blocks) {
    static final int SCHEMA_VERSION = 1;
    static final String AIR_BLOCK_ID = "minecraft:air";

    VoxelFile {
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("voxel schema must be 1");
        }
        Objects.requireNonNull(plotId, "plotId");
        origin = List.copyOf(origin);
        size = List.copyOf(size);
        palette = List.copyOf(palette);
        blocks = List.copyOf(blocks);
        if (origin.size() != 3 || size.size() != 3) {
            throw new IllegalArgumentException("origin and size must contain three integers");
        }
        if (size.get(0) <= 0 || size.get(1) < 0 || size.get(2) <= 0) {
            throw new IllegalArgumentException("trimmed voxel size is invalid");
        }
        if (palette.isEmpty() || !AIR_BLOCK_ID.equals(palette.getFirst())) {
            throw new IllegalArgumentException("palette index 0 must be minecraft:air");
        }
        int expectedBlocks =
                Math.multiplyExact(
                        Math.multiplyExact(size.get(0), size.get(1)), size.get(2));
        if (blocks.size() != expectedBlocks) {
            throw new IllegalArgumentException("blocks length must equal the trimmed volume");
        }
        for (Integer index : blocks) {
            if (index == null || index < 0 || index >= palette.size()) {
                throw new IllegalArgumentException("block palette index is out of range");
            }
        }
    }
}
