package io.github.agorokh.scenariocraft.renderer;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class VoxelPlot {
    private static final Gson GSON = new Gson();

    private final String plotId;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<String> palette;
    private final int[] blocks;

    private VoxelPlot(String plotId, int sizeX, int sizeY, int sizeZ,
                      List<String> palette, int[] blocks) {
        this.plotId = plotId;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette);
        this.blocks = blocks.clone();
    }

    public static VoxelPlot read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Document document = GSON.fromJson(reader, Document.class);
            if (document == null) {
                throw new IllegalArgumentException("Voxel JSON must contain an object");
            }
            return validate(document);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Invalid voxel JSON: " + exception.getMessage(), exception);
        }
    }

    private static VoxelPlot validate(Document document) {
        if (document.schema != 1) {
            throw new IllegalArgumentException("Unsupported voxel schema: " + document.schema);
        }
        if (document.plot_id == null || document.plot_id.isBlank()) {
            throw new IllegalArgumentException("plot_id must be present");
        }
        if (document.origin == null || document.origin.length != 3) {
            throw new IllegalArgumentException("origin must contain exactly three integers");
        }
        if (document.size == null || document.size.length != 3) {
            throw new IllegalArgumentException("size must contain exactly three integers");
        }
        int sizeX = document.size[0];
        int sizeY = document.size[1];
        int sizeZ = document.size[2];
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
            throw new IllegalArgumentException("size values must be non-negative");
        }
        if (document.palette == null || document.palette.isEmpty()
                || !"minecraft:air".equals(document.palette.getFirst())) {
            throw new IllegalArgumentException("palette[0] must be minecraft:air");
        }
        if (document.blocks == null) {
            throw new IllegalArgumentException("blocks must be a JSON array of integers");
        }
        long expected = (long) sizeX * sizeY * sizeZ;
        if (expected > Integer.MAX_VALUE || document.blocks.length != expected) {
            throw new IllegalArgumentException(
                    "blocks length must equal size[0] * size[1] * size[2]");
        }
        for (int paletteIndex : document.blocks) {
            if (paletteIndex < 0 || paletteIndex >= document.palette.size()) {
                throw new IllegalArgumentException("blocks contains an invalid palette index");
            }
        }
        return new VoxelPlot(document.plot_id, sizeX, sizeY, sizeZ,
                document.palette, document.blocks);
    }

    public String plotId() { return plotId; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }

    public int blockAt(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            return 0;
        }
        return blocks[x + sizeX * (z + sizeZ * y)];
    }

    public String blockIdAt(int x, int y, int z) {
        return palette.get(blockAt(x, y, z));
    }

    private static final class Document {
        private int schema;
        private String plot_id;
        private int[] origin;
        private int[] size;
        private List<String> palette;
        private int[] blocks;
    }
}
