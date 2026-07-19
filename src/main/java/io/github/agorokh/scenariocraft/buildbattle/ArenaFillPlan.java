package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

/** Pure clear-and-wall plan that is later consumed by the per-tick block queue. */
public record ArenaFillPlan(List<BlockFill> fills, long totalBlockMutations) {
    private static final Material WALL_MATERIAL = Material.WHITE_CONCRETE;

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
            List<PlotBounds> plots, int floorY, int wallHeight) {
        if (plots.isEmpty()) {
            throw new IllegalArgumentException("at least one plot is required");
        }
        if (wallHeight <= 0) {
            throw new IllegalArgumentException("wallHeight must be positive");
        }
        int minY = Math.addExact(floorY, 1);
        int maxY = Math.addExact(floorY, wallHeight);
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
        }
        return new ArenaFillPlan(fills, total);
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
