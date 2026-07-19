package io.github.agorokh.scenariocraft.buildbattle;

/** Inclusive three-dimensional block bounds. */
public record Cuboid(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    public Cuboid {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("cuboid minimums must not exceed maximums");
        }
    }

    public long blockCount() {
        long width = (long) maxX - minX + 1L;
        long height = (long) maxY - minY + 1L;
        long depth = (long) maxZ - minZ + 1L;
        return Math.multiplyExact(Math.multiplyExact(width, height), depth);
    }
}
