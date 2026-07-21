package io.github.agorokh.scenariocraft.buildbattle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

/** A small resource-backed build that fills unclaimed plots in demo mode. */
public final class DemoSampleBuild {
    private static final int MAX_LINES = 256;
    private static final int MAX_LINE_LENGTH = 200;

    private final List<RelativeFill> fills;

    private DemoSampleBuild(List<RelativeFill> fills) {
        this.fills = List.copyOf(fills);
    }

    public static DemoSampleBuild empty() {
        return new DemoSampleBuild(List.of());
    }

    public static DemoSampleBuild load(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        List<RelativeFill> fills = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber > MAX_LINES) {
                    throw new IOException("demo sample build exceeds " + MAX_LINES + " lines");
                }
                if (line.length() > MAX_LINE_LENGTH) {
                    throw invalidLine(lineNumber, "line is too long");
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] fields = trimmed.split("\\s+");
                if (fields.length != 7) {
                    throw invalidLine(
                            lineNumber,
                            "expected MATERIAL minX maxX minY maxY minZ maxZ");
                }
                Material material = material(fields[0], lineNumber);
                if (material == Material.AIR
                        || material == Material.CAVE_AIR
                        || material == Material.VOID_AIR) {
                    throw invalidLine(lineNumber, "material must be a non-air Minecraft block");
                }
                int minX = coordinate(fields[1], lineNumber);
                int maxX = coordinate(fields[2], lineNumber);
                int minY = coordinate(fields[3], lineNumber);
                int maxY = coordinate(fields[4], lineNumber);
                int minZ = coordinate(fields[5], lineNumber);
                int maxZ = coordinate(fields[6], lineNumber);
                if (minX > maxX || minY > maxY || minZ > maxZ || minY < 0) {
                    throw invalidLine(lineNumber, "coordinate bounds are invalid");
                }
                fills.add(new RelativeFill(minX, maxX, minY, maxY, minZ, maxZ, material));
            }
        }
        if (fills.isEmpty()) {
            throw new IOException("demo sample build must contain at least one block fill");
        }
        return new DemoSampleBuild(fills);
    }

    public boolean isEmpty() {
        return fills.isEmpty();
    }

    /** Converts resource-relative fills into absolute arena mutations. */
    public List<BlockFill> placeIn(PlotBounds plot, int floorY, int wallHeight) {
        Objects.requireNonNull(plot, "plot");
        if (wallHeight <= 0) {
            throw new IllegalArgumentException("wallHeight must be positive");
        }
        int centerX = plot.centerX();
        int centerZ = plot.centerZ();
        List<BlockFill> placed = new ArrayList<>(fills.size());
        for (RelativeFill fill : fills) {
            int minX = Math.addExact(centerX, fill.minX());
            int maxX = Math.addExact(centerX, fill.maxX());
            int minY = Math.addExact(Math.addExact(floorY, 1), fill.minY());
            int maxY = Math.addExact(Math.addExact(floorY, 1), fill.maxY());
            int minZ = Math.addExact(centerZ, fill.minZ());
            int maxZ = Math.addExact(centerZ, fill.maxZ());
            if (minX < plot.minX()
                    || maxX > plot.maxX()
                    || minZ < plot.minZ()
                    || maxZ > plot.maxZ()
                    || fill.maxY() >= wallHeight) {
                throw new IllegalArgumentException(
                        "demo sample build does not fit the configured plot and wall height");
            }
            placed.add(
                    new BlockFill(
                            new Cuboid(minX, maxX, minY, maxY, minZ, maxZ),
                            fill.material()));
        }
        return List.copyOf(placed);
    }

    private static int coordinate(String value, int lineNumber) throws IOException {
        if (!value.matches("-?[0-9]+")) {
            throw invalidLine(lineNumber, "coordinates must be integers");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalidLine(lineNumber, "coordinate is outside the integer range");
        }
    }

    private static Material material(String value, int lineNumber) throws IOException {
        if (!value.matches("[A-Z0-9_]+")) {
            throw invalidLine(lineNumber, "material must use an uppercase Bukkit name");
        }
        try {
            return Material.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalidLine(lineNumber, "material is unknown");
        }
    }

    private static IOException invalidLine(int lineNumber, String reason) {
        return new IOException("invalid demo sample build line " + lineNumber + ": " + reason);
    }

    private record RelativeFill(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            Material material) {}
}
