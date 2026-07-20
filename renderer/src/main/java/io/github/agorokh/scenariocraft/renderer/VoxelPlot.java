package io.github.agorokh.scenariocraft.renderer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class VoxelPlot {
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
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new IllegalArgumentException("Voxel JSON must contain an object");
            }
            return validate(readDocument(root.getAsJsonObject()));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Invalid voxel JSON: " + exception.getMessage(), exception);
        }
    }

    private static Document readDocument(JsonObject root) {
        Document document = new Document();
        document.schema = requireInteger(root, "schema");
        document.plot_id = requireString(root, "plot_id");
        document.origin = requireIntegerArray(root, "origin");
        document.size = requireIntegerArray(root, "size");
        document.palette = requireStringArray(root, "palette");
        document.blocks = requireIntegerArray(root, "blocks");
        return document;
    }

    private static int requireInteger(JsonObject root, String name) {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException(name + " must be a JSON integer");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        String token = primitive.getAsString();
        if (!primitive.isNumber() || !token.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException(name + " must be a JSON integer");
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must fit in a 32-bit integer", exception);
        }
    }

    private static String requireString(JsonObject root, String name) {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a JSON string");
        }
        return element.getAsString();
    }

    private static int[] requireIntegerArray(JsonObject root, String name) {
        JsonArray array = requireArray(root, name);
        int[] values = new int[array.size()];
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            if (!element.isJsonPrimitive()) {
                throw new IllegalArgumentException(name + " must be a JSON array of integers");
            }
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            String token = primitive.getAsString();
            if (!primitive.isNumber() || !token.matches("-?(0|[1-9][0-9]*)")) {
                throw new IllegalArgumentException(name + " must be a JSON array of integers");
            }
            try {
                values[index] = Integer.parseInt(token);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        name + " values must fit in a 32-bit integer", exception);
            }
        }
        return values;
    }

    private static List<String> requireStringArray(JsonObject root, String name) {
        JsonArray array = requireArray(root, name);
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + " must be a JSON array of strings");
            }
            values.add(element.getAsString());
        }
        return values;
    }

    private static JsonArray requireArray(JsonObject root, String name) {
        JsonElement element = root.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be a JSON array");
        }
        return element.getAsJsonArray();
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
        if (document.palette.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("palette entries must be non-blank strings");
        }
        if (document.blocks == null) {
            throw new IllegalArgumentException("blocks must be a JSON array of integers");
        }
        long expected;
        try {
            expected = Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("size volume is too large", exception);
        }
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
