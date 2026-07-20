package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;

/** Pure arena mutation plan that is later consumed by the per-tick block queue. */
public record ArenaFillPlan(List<BlockFill> fills, long totalBlockMutations) {
    private static final Material WALL_MATERIAL = Material.WHITE_CONCRETE;
    private static final Material CAP_MATERIAL = Material.BARRIER;

    public ArenaFillPlan {
        fills = List.copyOf(fills);
        if (totalBlockMutations < 0) {
            throw new IllegalArgumentException("totalBlockMutations must be non-negative");
        }
        long calculatedTotal = 0L;
        for (BlockFill fill : fills) {
            calculatedTotal = Math.addExact(calculatedTotal, fill.bounds().blockCount());
        }
        if (calculatedTotal != totalBlockMutations) {
            throw new IllegalArgumentException(
                    "totalBlockMutations must equal the planned fill volume");
        }
    }

    public static ArenaFillPlan forPlots(
            List<PlotBounds> plots,
            int floorY,
            int wallHeight,
            SecretChestPosition secretChest) {
        if (plots.isEmpty()) {
            throw new IllegalArgumentException("at least one plot is required");
        }
        if (wallHeight <= 0) {
            throw new IllegalArgumentException("wallHeight must be positive");
        }
        java.util.Objects.requireNonNull(secretChest, "secretChest");
        int minY = Math.addExact(floorY, 1);
        int maxY = Math.addExact(floorY, wallHeight);
        int capY = Math.addExact(maxY, 1);
        List<BlockFill> fills =
                new ArrayList<>(Math.addExact(Math.multiplyExact(plots.size(), 6), 1));
        long total = 0L;

        for (PlotBounds plot : plots) {
            int outerMinX = Math.subtractExact(plot.minX(), 1);
            int outerMaxX = Math.addExact(plot.maxX(), 1);
            int outerMinZ = Math.subtractExact(plot.minZ(), 1);
            int outerMaxZ = Math.addExact(plot.maxZ(), 1);

            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMinX,
                                    outerMaxX,
                                    minY,
                                    capY,
                                    outerMinZ,
                                    outerMaxZ),
                            Material.AIR);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMinX,
                                    outerMaxX,
                                    minY,
                                    maxY,
                                    outerMinZ,
                                    outerMinZ),
                            WALL_MATERIAL);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMinX,
                                    outerMaxX,
                                    minY,
                                    maxY,
                                    outerMaxZ,
                                    outerMaxZ),
                            WALL_MATERIAL);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMinX,
                                    outerMinX,
                                    minY,
                                    maxY,
                                    plot.minZ(),
                                    plot.maxZ()),
                            WALL_MATERIAL);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMaxX,
                                    outerMaxX,
                                    minY,
                                    maxY,
                                    plot.minZ(),
                                    plot.maxZ()),
                            WALL_MATERIAL);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    plot.minX(),
                                    plot.maxX(),
                                    capY,
                                    capY,
                                    plot.minZ(),
                                    plot.maxZ()),
                            CAP_MATERIAL);
        }
        total = add(fills, total, secretChest.asCuboid(), Material.CHEST);
        return new ArenaFillPlan(fills, total);
    }

    public static ArenaFillPlan forWallRemoval(
            List<PlotBounds> plots, int floorY, int wallHeight) {
        if (plots.isEmpty()) {
            throw new IllegalArgumentException("at least one plot is required");
        }
        if (wallHeight <= 0) {
            throw new IllegalArgumentException("wallHeight must be positive");
        }
        int minY = Math.addExact(floorY, 1);
        int maxY = Math.addExact(floorY, wallHeight);
        int capY = Math.addExact(maxY, 1);
        List<BlockFill> fills = new ArrayList<>(Math.multiplyExact(plots.size(), 5));
        long total = 0L;

        for (PlotBounds plot : plots) {
            int outerMinX = Math.subtractExact(plot.minX(), 1);
            int outerMaxX = Math.addExact(plot.maxX(), 1);
            int outerMinZ = Math.subtractExact(plot.minZ(), 1);
            int outerMaxZ = Math.addExact(plot.maxZ(), 1);

            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMinX,
                                    outerMaxX,
                                    minY,
                                    maxY,
                                    outerMinZ,
                                    outerMinZ),
                            Material.AIR);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMinX,
                                    outerMaxX,
                                    minY,
                                    maxY,
                                    outerMaxZ,
                                    outerMaxZ),
                            Material.AIR);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMinX,
                                    outerMinX,
                                    minY,
                                    maxY,
                                    plot.minZ(),
                                    plot.maxZ()),
                            Material.AIR);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    outerMaxX,
                                    outerMaxX,
                                    minY,
                                    maxY,
                                    plot.minZ(),
                                    plot.maxZ()),
                            Material.AIR);
            total =
                    add(
                            fills,
                            total,
                            new Cuboid(
                                    plot.minX(),
                                    plot.maxX(),
                                    capY,
                                    capY,
                                    plot.minZ(),
                                    plot.maxZ()),
                            Material.AIR);
        }
        return new ArenaFillPlan(fills, total);
    }

    public List<ChunkCoordinate> chunkCoordinates() {
        Set<ChunkCoordinate> chunks = new LinkedHashSet<>();
        for (BlockFill fill : fills) {
            Cuboid bounds = fill.bounds();
            int minChunkX = Math.floorDiv(bounds.minX(), 16);
            int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
            int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
            int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    chunks.add(new ChunkCoordinate(chunkX, chunkZ));
                }
            }
        }
        return List.copyOf(chunks);
    }

    private static long add(
            List<BlockFill> fills,
            long currentTotal,
            Cuboid bounds,
            Material material) {
        fills.add(new BlockFill(bounds, material));
        return Math.addExact(currentTotal, bounds.blockCount());
    }
}
