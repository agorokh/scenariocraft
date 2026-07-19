package io.github.agorokh.scenariocraft.buildbattle;

/** Inclusive horizontal bounds for one build plot. */
public record PlotBounds(int minX, int maxX, int minZ, int maxZ) {
    public PlotBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("plot minimums must not exceed maximums");
        }
    }

    public int width() {
        return Math.addExact(Math.subtractExact(maxX, minX), 1);
    }

    public int depth() {
        return Math.addExact(Math.subtractExact(maxZ, minZ), 1);
    }

    public int centerX() {
        return Math.toIntExact(((long) minX + maxX) / 2L);
    }

    public int centerZ() {
        return Math.toIntExact(((long) minZ + maxZ) / 2L);
    }

    public boolean overlaps(PlotBounds other) {
        return minX <= other.maxX
                && maxX >= other.minX
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
    }
}
