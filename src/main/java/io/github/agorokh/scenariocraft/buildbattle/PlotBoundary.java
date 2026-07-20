package io.github.agorokh.scenariocraft.buildbattle;

import java.util.Objects;

/** Personal border and editable-height geometry for one contestant plot. */
public record PlotBoundary(PlotBounds plot, int minBuildY, int maxBuildY) {
    public PlotBoundary {
        Objects.requireNonNull(plot, "plot");
        if (plot.width() != plot.depth()) {
            throw new IllegalArgumentException("personal world borders require square plots");
        }
        if (minBuildY > maxBuildY) {
            throw new IllegalArgumentException(
                    "minimum build height must not exceed maximum build height");
        }
    }

    public static PlotBoundary forPlot(PlotBounds plot, int floorY, int wallHeight) {
        if (wallHeight <= 0) {
            throw new IllegalArgumentException("wallHeight must be positive");
        }
        return new PlotBoundary(
                plot,
                Math.addExact(floorY, 1),
                Math.addExact(floorY, wallHeight));
    }

    public double borderCenterX() {
        return ((long) plot.minX() + plot.maxX() + 1L) / 2.0;
    }

    public double borderCenterZ() {
        return ((long) plot.minZ() + plot.maxZ() + 1L) / 2.0;
    }

    public double borderSize() {
        return plot.width();
    }

    public int capY() {
        return Math.addExact(maxBuildY, 1);
    }

    public boolean containsEditableBlock(int x, int y, int z) {
        return x >= plot.minX()
                && x <= plot.maxX()
                && y >= minBuildY
                && y <= maxBuildY
                && z >= plot.minZ()
                && z <= plot.maxZ();
    }
}
