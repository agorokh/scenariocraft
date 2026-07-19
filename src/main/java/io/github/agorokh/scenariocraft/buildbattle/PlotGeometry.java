package io.github.agorokh.scenariocraft.buildbattle;

import java.util.ArrayList;
import java.util.List;

/** Computes deterministic plot bounds on concentric square rings around an arena hub. */
public final class PlotGeometry {
    private PlotGeometry() {}

    public static List<PlotBounds> aroundHub(
            int hubX, int hubZ, int count, int plotSize, int plotSpacing) {
        if (count <= 0 || plotSize <= 0 || plotSpacing <= 0) {
            throw new IllegalArgumentException("plot geometry values must be positive");
        }
        if ((long) plotSpacing <= (long) plotSize + 1L) {
            throw new IllegalArgumentException(
                    "plot spacing must include room for the surrounding walls");
        }

        int lowerHalf = plotSize / 2;
        int upperHalf = plotSize - lowerHalf - 1;
        List<PlotBounds> plots = new ArrayList<>(count);
        int ring = 1;
        while (plots.size() < count) {
            List<GridOffset> ringOffsets = squareRing(ring);
            int plotsOnRing = Math.min(count - plots.size(), ringOffsets.size());
            for (int index = 0; index < plotsOnRing; index++) {
                int offsetIndex =
                        Math.toIntExact((long) index * ringOffsets.size() / plotsOnRing);
                GridOffset offset = ringOffsets.get(offsetIndex);
                long centerX = hubX + (long) offset.x() * plotSpacing;
                long centerZ = hubZ + (long) offset.z() * plotSpacing;
                plots.add(
                        new PlotBounds(
                                Math.toIntExact(centerX - lowerHalf),
                                Math.toIntExact(centerX + upperHalf),
                                Math.toIntExact(centerZ - lowerHalf),
                                Math.toIntExact(centerZ + upperHalf)));
            }
            ring++;
        }
        return List.copyOf(plots);
    }

    private static List<GridOffset> squareRing(int radius) {
        List<GridOffset> offsets = new ArrayList<>(Math.multiplyExact(radius, 8));
        offsets.add(new GridOffset(0, -radius));
        for (int x = 1; x <= radius; x++) {
            offsets.add(new GridOffset(x, -radius));
        }
        for (int z = -radius + 1; z <= radius; z++) {
            offsets.add(new GridOffset(radius, z));
        }
        for (int x = radius - 1; x >= -radius; x--) {
            offsets.add(new GridOffset(x, radius));
        }
        for (int z = radius - 1; z >= -radius; z--) {
            offsets.add(new GridOffset(-radius, z));
        }
        for (int x = -radius + 1; x < 0; x++) {
            offsets.add(new GridOffset(x, -radius));
        }
        return offsets;
    }

    private record GridOffset(int x, int z) {}
}
